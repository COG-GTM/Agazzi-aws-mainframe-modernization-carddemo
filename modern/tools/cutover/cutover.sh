#!/usr/bin/env bash
# Runs the Java cutover tools (single-file sources in java/) on the carddemo module classpath.
#   tools/cutover/cutover.sh reconcile [args]   -> java/Reconcile.java
#   tools/cutover/cutover.sh compare   [args]   -> java/ParallelRunCompare.java
# Classpath: $CARDDEMO_CUTOVER_CP if set, else built once from the Maven reactor into
# target/cutover/classpath.txt (needs `./mvnw install -DskipTests -pl carddemo-parity,carddemo-app -am`).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MODERN="$(cd "$HERE/../.." && pwd)"
tool="${1:?usage: cutover.sh reconcile|compare [args]}"; shift
case "$tool" in
  reconcile) src="$HERE/java/Reconcile.java" ;;
  compare) src="$HERE/java/ParallelRunCompare.java" ;;
  *) echo "unknown tool: $tool" >&2; exit 2 ;;
esac
cp="${CARDDEMO_CUTOVER_CP:-}"
if [[ -z "$cp" ]]; then
  cpfile="$MODERN/target/cutover/classpath.txt"
  if [[ ! -s "$cpfile" ]]; then
    mkdir -p "$(dirname "$cpfile")"
    (cd "$MODERN" && ./mvnw -B -q dependency:build-classpath -pl carddemo-app \
        -Dmdep.includeScope=runtime -Dmdep.outputFile="$cpfile") >&2
    parity=""
    for j in "$MODERN"/carddemo-parity/target/carddemo-parity-*.jar ~/.m2/repository/com/carddemo/carddemo-parity/*/carddemo-parity-*.jar; do
      [[ -f "$j" && "$j" != *sources* ]] && { parity="$j"; break; }
    done
    [[ -n "$parity" ]] || { echo "carddemo-parity jar not found: ./mvnw install -DskipTests" >&2; exit 2; }
    printf ':%s' "$parity" >> "$cpfile"
  fi
  cp="$(cat "$cpfile")"
fi
exec java -Dcutover.home="$HERE" -cp "$cp" "$src" "$@"
