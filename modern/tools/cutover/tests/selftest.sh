#!/usr/bin/env bash
# Self-test of the cutover tooling against today's seed (V1 baseline + V2 seed) and goldens.
# Needs: docker (or CARDDEMO_SELFTEST_DB_URL + psql access via PG* env), java 21, python3 + PyYAML,
# built modules (./mvnw -B install -DskipTests). Run from modern/: tools/cutover/tests/selftest.sh
set -euo pipefail
MODERN="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$MODERN"
T=target/cutover/selftest; rm -rf "$T"; mkdir -p "$T"
pass=0; fail() { echo "SELFTEST FAIL: $*" >&2; exit 1; }
ok() { echo "ok - $*"; pass=$((pass + 1)); }

if [[ -z "${CARDDEMO_SELFTEST_DB_URL:-}" ]]; then
  port="${CARDDEMO_SELFTEST_PORT:-55433}"
  docker rm -f cutover-selftest-pg >/dev/null 2>&1 || true
  docker run -d --name cutover-selftest-pg -e POSTGRES_DB=carddemo -e POSTGRES_USER=carddemo \
    -e POSTGRES_PASSWORD=selftest -p "$port:5432" "${CARDDEMO_PG_IMAGE:-postgres:16-alpine}" >/dev/null
  trap 'docker rm -f cutover-selftest-pg >/dev/null 2>&1 || true' EXIT
  for _ in $(seq 60); do docker exec cutover-selftest-pg pg_isready -U carddemo -d carddemo >/dev/null 2>&1 && break; sleep 1; done
  sleep 2
  psqlx() { docker exec -i cutover-selftest-pg psql -q -v ON_ERROR_STOP=1 -U carddemo -d carddemo "$@"; }
  export CARDDEMO_DB_PASSWORD=selftest
  DB_URL="jdbc:postgresql://localhost:$port/carddemo"
else
  psqlx() { psql -q -v ON_ERROR_STOP=1 "$@"; }
  DB_URL="$CARDDEMO_SELFTEST_DB_URL"
fi
psqlx < carddemo-domain/src/main/resources/db/migration/V1__baseline.sql
psqlx < carddemo-domain/src/main/resources/db/seed/V2__seed_sample_data.sql
ok "seed V1+V2 loaded"

# 1. reconciliation: seed DB == app/data/ASCII
tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root ../app/data/ASCII --out "$T/rec" >/dev/null \
  || fail "reconcile seed vs ASCII"
python3 -c "import json,sys; d=json.load(open('$T/rec/reconciliation.json')); sys.exit(0 if d['ok'] and len(d['datasets'])==11 and all(x['status']=='MATCH' for x in d['datasets']) else 1)" \
  || fail "reconciliation.json"
ok "reconcile ASCII: 11/11 MATCH"

# 2. reconciliation detects a changed DB row (and only that dataset)
psqlx -c "UPDATE account SET acct_curr_bal = acct_curr_bal + 1 WHERE acct_id = 1"
if tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root ../app/data/ASCII --datasets ACCTDATA,CARDDATA --out "$T/rec-bad" >/dev/null; then
  fail "reconcile did not detect the changed balance"
fi
grep -q "ACCTDATA | \`account\` | MISMATCH" "$T/rec-bad/reconciliation.md" && grep -q "CARDDATA | \`card\` | MATCH" "$T/rec-bad/reconciliation.md" \
  && grep -q "differs 1 acct_curr_bal: file \[194.00\] db \[195.00\]" "$T/rec-bad/reconciliation.md" || fail "mismatch report"
psqlx -c "UPDATE account SET acct_curr_bal = acct_curr_bal - 1 WHERE acct_id = 1"
ok "reconcile detects a +1.00 balance drift on account 00000000001"

# 3. EBCDIC unloads decode with the CP037 codecs (known sample-data drift, see docs/cutover/reconciliation.md)
tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root ../app/data/EBCDIC --format ebcdic \
  --datasets CARDDATA,CARDXREF,CUSTDATA,TRANTYPE,TRANCATG,TCATBALF,DALYTRAN,USRSEC --out "$T/rec-ebcdic" >/dev/null \
  || fail "reconcile EBCDIC"
ok "reconcile EBCDIC (CP037): 8 datasets MATCH"

# 4. parallel run: identical outputs -> GO, one changed field -> NO-GO with field-level diff
G=carddemo-parity/src/test/resources/golden/batch
for side in mf java; do for j in POSTTRAN INTCALC TRANREPT CREASTMT; do mkdir -p "$T/pr/$side"; cp -r "$G/$j/expected" "$T/pr/$side/$j"; done; done
tools/cutover/cutover.sh compare --mainframe "$T/pr/mf" --java "$T/pr/java" --date 2022-07-18 --out "$T/pr/out" >/dev/null \
  || fail "parallel run identical outputs"
ok "parallel run: identical outputs -> OK"
sed -i '1s/^\(.\{20\}\)./\19/' "$T/pr/java/POSTTRAN/ACCTDATA.dat"
if tools/cutover/cutover.sh compare --mainframe "$T/pr/mf" --java "$T/pr/java" --date 2022-07-18 --out "$T/pr/out" >/dev/null; then
  fail "parallel run did not detect the change"
fi
grep -q "POSTTRAN ACCTDATA record 1 field ACCT-CURR-BAL" "$T/pr/out/2022-07-18/parallel-run-report.md" || fail "diff report"
ok "parallel run: changed ACCT-CURR-BAL -> NO-GO with field diff"

# 5. rollback wrapper on an existing export (ASCII seed as stand-in for relationalToVsam output)
tools/cutover/rollback/relational-to-vsam.sh --out "$T/rb" --format ascii --from-dir ../app/data/ASCII \
  --db-url "$DB_URL" --no-freeze-check >/dev/null || fail "rollback wrapper"
grep -q "REPRO INFILE(IN) OUTDATASET(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) REPLACE" "$T/rb/ROLLBACK.jcl" \
  && [[ $(wc -l < "$T/rb/SHA256SUMS") -eq 9 ]] || fail "ROLLBACK.jcl / SHA256SUMS"
ok "rollback wrapper: export verified, ROLLBACK.jcl + SHA256SUMS"

# 6. smoke test client: health PASS against a stub, PENDING endpoints accepted only without --strict
python3 tools/cutover/tests/stub_server.py 18099 & stub=$!
trap 'kill $stub 2>/dev/null; docker rm -f cutover-selftest-pg >/dev/null 2>&1 || true' EXIT
sleep 1
python3 tools/cutover/smoke_test.py --base-url http://localhost:18099 --allow-write --report "$T/smoke.json" >/dev/null || fail "smoke (stub, full screens)"
python3 -c "import json,sys; r={x['check']:x['status'] for x in json.load(open('$T/smoke.json'))['results']}; sys.exit(0 if r=={'health':'PASS','maintenance':'PASS','signon':'PASS','cavw':'PASS','ct02':'PASS','posttran':'SKIPPED'} else print(r) or 1)" \
  || fail "smoke results"
ok "smoke test: health, maintenance, sign-on, CAVW, CT02 PASS on contract stub"
STUB_MISSING=1 python3 tools/cutover/smoke_test.py --base-url http://localhost:18099 >/dev/null || fail "smoke non-strict"
if python3 tools/cutover/smoke_test.py --base-url http://localhost:18099/missing --strict >/dev/null; then fail "smoke strict"; fi
ok "smoke test: --strict turns PENDING (404) into NO-GO"

python3 tools/cutover/decommission_inventory.py --check >/dev/null || fail "decommission inventory stale"
ok "decommission inventory up to date"
echo "SELFTEST OK ($pass checks)"
