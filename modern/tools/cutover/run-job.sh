#!/usr/bin/env bash
# Launches one Spring Batch job by its JCL name (CONVENTIONS: job name = JCL name), on the target
# where the cutover runs. Exit codes: 0 COMPLETED, 1 other status, 3 = job not registered in the
# deployed app yet (explicit "No job found with name"), 5 = not launched within
# CARDDEMO_JOB_START_GRACE (default 900 s) after startup, 124 = CARDDEMO_JOB_TIMEOUT (default 4 h).
#   run-job.sh POSTTRAN [key=value ...]
# Modes (CARDDEMO_JOB_MODE):
#   local (default) java -jar $CARDDEMO_APP_JAR --spring.batch.job.name=<JOB> run.date=... [params]
#   ecs             aws ecs run-task on $CARDDEMO_ECS_CLUSTER with task definition
#                   $CARDDEMO_BATCH_TASKDEF (Terraform output batch_task_definition), waits and
#                   returns the container exit code.
set -euo pipefail
job="${1:?usage: run-job.sh <JOBNAME> [key=value ...]}"; shift
HERE="$(cd "$(dirname "$0")" && pwd)"
params=("run.date=$(date -u +%Y-%m-%dT%H:%M:%S)" "$@")
case "${CARDDEMO_JOB_MODE:-local}" in
  local)
    # The app is a servlet app (Fase0 security needs HttpSecurity), so the JVM keeps running after
    # the job: watch the log for the job's final status, then stop the JVM and map the status.
    jar="${CARDDEMO_APP_JAR:-$HERE/../../carddemo-app/target/carddemo-app-exec.jar}"
    log="$(mktemp)"; trap 'rm -f "$log"' EXIT
    java -jar "$jar" --spring.main.web-application-type=none --spring.batch.job.enabled=true \
      --spring.batch.job.name="$job" "${params[@]}" >"$log" 2>&1 &
    pid=$!
    status="" deadline=$((SECONDS + ${CARDDEMO_JOB_TIMEOUT:-14400}))
    while kill -0 "$pid" 2>/dev/null; do
      status=$(grep -o "Job: \[[^]]*name=$job\][^$]*status: \[[A-Z_]*\]" "$log" | grep -o "status: \[[A-Z_]*\]" | tail -1 || true)
      [[ -n "$status" ]] && break
      # Started but not launched within the grace period: a distinct failure (5), not proof the job
      # is unregistered (exit 3 is only the explicit "No job found"). Launched jobs only hit the timeout.
      if [[ -z "${started:-}" ]] && grep -q "Started .* in [0-9.]* seconds" "$log"; then started=$SECONDS; fi
      if [[ -n "${started:-}" ]] && ! grep -q "Job: \[.*name=$job\]\] launched" "$log" \
         && (( SECONDS - started > ${CARDDEMO_JOB_START_GRACE:-900} )); then
        kill "$pid"; wait "$pid" 2>/dev/null || true; tail -20 "$log"
        echo "run-job: $job not launched ${CARDDEMO_JOB_START_GRACE:-900}s after startup (CARDDEMO_JOB_START_GRACE): check runner/job repository" >&2; exit 5
      fi
      if (( SECONDS > deadline )); then
        kill "$pid"; wait "$pid" 2>/dev/null || true; tail -20 "$log"
        echo "run-job: $job TIMEOUT after ${CARDDEMO_JOB_TIMEOUT:-14400}s (CARDDEMO_JOB_TIMEOUT)" >&2; exit 124
      fi
      sleep 2
    done
    if [[ -n "$status" ]]; then
      kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true
      tail -20 "$log"; echo "run-job: $job $status"
      [[ "$status" == "status: [COMPLETED]" ]] && exit 0 || exit 1
    fi
    set +e; wait "$pid"; rc=$?; set -e
    tail -20 "$log"
    if grep -q "No job found with name\|No job configurations found\|Job name must be specified\|NoSuchJobException" "$log"; then
      echo "run-job: job $job not registered in $jar (module not integrated yet)" >&2; exit 3
    fi
    [[ $rc -ne 0 ]] && exit "$rc"
    echo "run-job: $job: application exited without a job status" >&2; exit 3 ;;
  ecs)
    : "${CARDDEMO_ECS_CLUSTER:?}" "${CARDDEMO_BATCH_TASKDEF:?}" "${CARDDEMO_SUBNETS:?}" "${CARDDEMO_SECURITY_GROUP:?}"
    overrides=$(python3 -c 'import json,sys; print(json.dumps({"containerOverrides":[{"name":"batch","command":["--spring.batch.job.name="+sys.argv[1]]+sys.argv[2:]}]}))' "$job" "${params[@]}")
    arn=$(aws ecs run-task --cluster "$CARDDEMO_ECS_CLUSTER" --launch-type FARGATE \
      --task-definition "$CARDDEMO_BATCH_TASKDEF" --overrides "$overrides" \
      --network-configuration "awsvpcConfiguration={subnets=[$CARDDEMO_SUBNETS],securityGroups=[$CARDDEMO_SECURITY_GROUP]}" \
      --query 'tasks[0].taskArn' --output text)
    echo "run-job: $job -> $arn"
    aws ecs wait tasks-stopped --cluster "$CARDDEMO_ECS_CLUSTER" --tasks "$arn"
    exit "$(aws ecs describe-tasks --cluster "$CARDDEMO_ECS_CLUSTER" --tasks "$arn" --query 'tasks[0].containers[0].exitCode' --output text)" ;;
  *) echo "unknown CARDDEMO_JOB_MODE" >&2; exit 2 ;;
esac
