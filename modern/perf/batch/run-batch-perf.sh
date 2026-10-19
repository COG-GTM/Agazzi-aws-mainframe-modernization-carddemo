#!/usr/bin/env bash
# 5B batch performance harness: loads the x1000 synthetic dataset, runs the Spring Batch jobs one by
# one through the carddemo-app CLI (`--spring.batch.job.name=<JCL name>`) and records elapsed time
# and throughput per job in $RESULTS/batch-results.{csv,md}.
#
#   modern/perf/batch/run-batch-perf.sh                      # defaults below
#   JOBS="POSTTRAN INTCALC" STORAGE=file modern/perf/batch/run-batch-perf.sh
#
# Env (all optional):
#   JAR        executable app jar      (default modern/carddemo-app/target/carddemo-app-exec.jar)
#   DATA       datagen output dir      (default modern/target/perf-data; generated if missing)
#   SCALE/SEED datagen parameters      (default 1000 / 20221231)
#   JOBS       jobs to run, in order   (default "POSTTRAN INTCALC TRANREPT CREASTMT fullBatchCycle")
#   STORAGE    db | file               (B1 job parameter `storage`, default db)
#   RELOAD     1 = reload the dataset before every job (isolated timings, default); 0 = chained run
#   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD   (default: the local docker-compose database)
#   PROFILE    Spring profile of the job JVM (default local: same Flyway locations, V1-V3 + seed V2,
#              as the app that created the schema; a different set fails Flyway validation)
#   JAVA_OPTS  JVM flags for the job JVM (e.g. "-Xmx2g -XX:+UseZGC")
#   JOB_ARGS   extra Spring args for every job (e.g. "--spring.datasource.hikari.maximum-pool-size=20")
#   TIMEOUT_S  per-job timeout in seconds (default 3600)
#   WEB_TYPE   spring.main.web-application-type for the job JVM (default none since Fase 5A F4:
#              the online security chain is servlet-only; the B5 CLI exit stops the JVM)
#   RESULTS    output dir              (default modern/target/perf-results/batch-<timestamp>)
# Exit status: 3 when any job is not OK (FAILED/TIMEOUT/NOT_AVAILABLE), after writing the report.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODERN="$(cd "$HERE/../.." && pwd)"
REPO="$(cd "$MODERN/.." && pwd)"

JAR="${JAR:-$MODERN/carddemo-app/target/carddemo-app-exec.jar}"
DATA="${DATA:-$MODERN/target/perf-data}"
SCALE="${SCALE:-1000}"
SEED="${SEED:-20221231}"
JOBS="${JOBS:-POSTTRAN INTCALC TRANREPT CREASTMT fullBatchCycle}"
STORAGE="${STORAGE:-db}"
RELOAD="${RELOAD:-1}"
TIMEOUT_S="${TIMEOUT_S:-3600}"
WEB_TYPE="${WEB_TYPE:-none}"
PROFILE="${PROFILE:-local}"
RESULTS="${RESULTS:-$MODERN/target/perf-results/batch-$(date -u +%Y%m%dT%H%M%SZ)}"
export PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5432}" PGDATABASE="${PGDATABASE:-carddemo}"
export PGUSER="${PGUSER:-carddemo}" PGPASSWORD="${PGPASSWORD:-carddemo}"
JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
now_ms() { date +%s%3N; }
need() { command -v "$1" >/dev/null || { log "missing tool: $1"; exit 2; }; }
need java
need psql

mkdir -p "$RESULTS"

# --- 1. synthetic data -------------------------------------------------------------------------
# Reuse DATA only if it was generated with the requested SCALE/SEED (k6/fixtures.json manifest).
manifest() { sed -n 's/^{"seed":\([0-9]*\),"scale":\([0-9]*\),.*/\2 \1/p' "$DATA/k6/fixtures.json" 2>/dev/null; }
if [[ -f "$DATA/load.sql" && "$(manifest)" != "$SCALE $SEED" ]]; then
  log "dataset in $DATA is scale/seed '$(manifest)', requested '$SCALE $SEED': regenerating"
  rm -rf "$DATA/ascii" "$DATA/csv" "$DATA/datasets" "$DATA/k6" "$DATA/load.sql"
fi
if [[ ! -f "$DATA/load.sql" ]]; then
  DG="$MODERN/perf/datagen/target/carddemo-perf-datagen-0.1.0-SNAPSHOT.jar"
  if [[ ! -f "$DG" ]]; then
    log "building datagen"
    (cd "$MODERN" && ./mvnw -B -q install -pl carddemo-copybook -am -DskipTests \
      && ./mvnw -B -q -f perf/datagen/pom.xml package -DskipTests)
  fi
  log "generating x$SCALE dataset (seed $SEED) into $DATA"
  java -jar "$DG" --sample "$REPO/app/data/ASCII" --out "$DATA" --scale "$SCALE" --seed "$SEED" \
    | tee "$RESULTS/datagen.log"
fi

count_lines() { if [[ -f "$1" ]]; then wc -l <"$1" | tr -d ' '; else echo 0; fi; }
N_DALY=$(count_lines "$DATA/ascii/dailytran.txt")
N_TRAN=$(count_lines "$DATA/ascii/transact.txt")
N_TCAT=$(count_lines "$DATA/ascii/tcatbal.txt")
N_ACCT=$(count_lines "$DATA/ascii/acctdata.txt")

# Records each job reads from its driving dataset (the JCL input DD), used for throughput.
input_records() {
  case "$1" in
    POSTTRAN | CBTRN01C) echo "$N_DALY DALYTRAN" ;;
    INTCALC) echo "$N_TCAT TCATBALF" ;;
    TRANREPT | CREASTMT | TRANBKP | COMBTRAN | TRANIDX) echo "$N_TRAN TRANSACT" ;;
    fullBatchCycle) echo "$N_DALY DALYTRAN" ;;
    *) echo "$N_ACCT ACCTDATA" ;;
  esac
}

load_db() {
  local t0 t1
  t0=$(now_ms)
  (cd "$DATA" && psql -q -v ON_ERROR_STOP=1 -f load.sql) >"$RESULTS/load-$1.log" 2>&1
  t1=$(now_ms)
  echo $((t1 - t0))
}

# --- 2. initial load ---------------------------------------------------------------------------
log "loading dataset into $JDBC_URL"
LOAD_MS=$(load_db initial)
ROWS=$(psql -At -c "SELECT (SELECT count(*) FROM account)||','||(SELECT count(*) FROM card)||','||(SELECT count(*) FROM transaction)||','||(SELECT count(*) FROM daily_transaction)")
log "load: ${LOAD_MS} ms (account,card,transaction,daily_transaction = $ROWS)"

CSV="$RESULTS/batch-results.csv"
echo "job,storage,status,exit_code,elapsed_ms,input_dataset,input_records,records_per_s,batch_status,reload_ms" >"$CSV"

# --- 3. jobs -----------------------------------------------------------------------------------
RUN=0
NOT_OK=0
for JOB in $JOBS; do
  RUN=$((RUN + 1))
  RELOAD_MS=0
  if [[ "$RELOAD" == "1" && $RUN -gt 1 ]]; then
    RELOAD_MS=$(load_db "$JOB")
  fi
  read -r NREC DSN <<<"$(input_records "$JOB")"
  WORK="$RESULTS/work-$JOB"
  mkdir -p "$WORK"
  if [[ "$STORAGE" == "file" ]]; then
    cp "$DATA"/datasets/*.dat "$WORK/"
  fi
  log "run $JOB (storage=$STORAGE, input $DSN=$NREC)"
  T0=$(now_ms)
  set +e
  # shellcheck disable=SC2086
  timeout "$TIMEOUT_S" java ${JAVA_OPTS:-} -jar "$JAR" \
    --spring.batch.job.name="$JOB" \
    --spring.batch.job.enabled=true \
    --spring.main.web-application-type="$WEB_TYPE" --server.port=0 \
    --spring.profiles.active="$PROFILE" \
    --spring.datasource.url="$JDBC_URL" \
    --spring.datasource.username="$PGUSER" \
    --spring.datasource.password="$PGPASSWORD" \
    --carddemo.orchestration.datasets-dir="$WORK" \
    ${JOB_ARGS:-} \
    storage="$STORAGE" datasetDir="$WORK" run.id="$(date +%s%N)" \
    >"$RESULTS/$JOB.log" 2>&1
  RC=$?
  set -e
  T1=$(now_ms)
  ELAPSED=$((T1 - T0))
  BSTATUS=$(psql -At -c "SELECT status FROM batch_job_execution e JOIN batch_job_instance i ON i.job_instance_id=e.job_instance_id WHERE i.job_name='$JOB' ORDER BY e.job_execution_id DESC LIMIT 1" 2>/dev/null || true)
  if [[ $RC -eq 0 && "$BSTATUS" == "COMPLETED" ]]; then
    STATUS=OK
  elif grep -qiE "No job (found|configurations? found)|job.*not (found|registered)|NoSuchJob" "$RESULTS/$JOB.log"; then
    STATUS=NOT_AVAILABLE
  elif [[ $RC -eq 124 ]]; then
    STATUS=TIMEOUT
  elif [[ -z "$BSTATUS" ]]; then
    STATUS=NOT_AVAILABLE
  else
    STATUS=FAILED
  fi
  RPS=n/a
  if [[ "$STATUS" == "OK" && $ELAPSED -gt 0 ]]; then
    RPS=$(awk -v n="$NREC" -v ms="$ELAPSED" 'BEGIN { printf "%.1f", n * 1000 / ms }')
  fi
  echo "$JOB,$STORAGE,$STATUS,$RC,$ELAPSED,$DSN,$NREC,$RPS,${BSTATUS:-none},$RELOAD_MS" >>"$CSV"
  log "$JOB: $STATUS rc=$RC ${ELAPSED} ms, ${RPS} rec/s (batch status: ${BSTATUS:-none})"
  [[ "$STATUS" == "OK" ]] || NOT_OK=$((NOT_OK + 1))
done

# --- 4. report ---------------------------------------------------------------------------------
{
  echo "# Batch perf run $(basename "$RESULTS")"
  echo
  echo "- dataset: \`$DATA\` (scale $SCALE, seed $SEED), load ${LOAD_MS} ms, rows account,card,transaction,daily_transaction = $ROWS"
  echo "- jar: \`$(basename "$JAR")\`, storage=$STORAGE, reload between jobs=$RELOAD, JAVA_OPTS=\`${JAVA_OPTS:-}\`"
  echo "- host: $(nproc) vCPU, $(awk '/MemTotal/ {printf "%.1f GiB", $2/1048576}' /proc/meminfo), $(java -version 2>&1 | head -1)"
  echo
  echo "| job | status | elapsed ms | input | records | rec/s | Spring Batch status |"
  echo "|---|---|---:|---|---:|---:|---|"
  tail -n +2 "$CSV" | while IFS=, read -r j s st rc ms dsn n rps bs rl; do
    echo "| $j | $st (rc=$rc) | $ms | $dsn | $n | $rps | $bs |"
  done
} >"$RESULTS/batch-results.md"
log "results: $RESULTS/batch-results.md"
cat "$RESULTS/batch-results.md"
if [[ $NOT_OK -gt 0 ]]; then
  log "$NOT_OK job(s) not OK"
  exit 3
fi
