#!/usr/bin/env bash
# Launches one Spring Batch job by its JCL name (CONVENTIONS: job name = JCL name), on the target
# where the cutover runs. Exit codes: job exit code, 3 = job not registered in the deployed app yet.
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
    jar="${CARDDEMO_APP_JAR:-$HERE/../../carddemo-app/target/carddemo-app-exec.jar}"
    log="$(mktemp)"
    set +e
    # web stays on (Fase0 security needs HttpSecurity) on a random port; exits when the job ends.
    java -jar "$jar" --server.port=0 --spring.batch.job.enabled=true \
      --spring.batch.job.name="$job" "${params[@]}" >"$log" 2>&1
    rc=$?
    set -e
    tail -20 "$log"
    if grep -q "No job found with name\|No job configurations found\|Job name must be specified" "$log" \
       || { [[ $rc -eq 0 ]] && ! grep -q "Job: \[.*$job" "$log"; }; then
      echo "run-job: job $job not registered in $jar (module not integrated yet)" >&2
      exit 3
    fi
    exit $rc ;;
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
