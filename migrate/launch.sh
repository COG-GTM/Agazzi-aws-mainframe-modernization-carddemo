#!/usr/bin/env bash
#
# Lancia le sessioni Devin per la migrazione CardDemo (COBOL/CICS/VSAM ->
# Java 21 + Spring Boot 3 + PostgreSQL), una sessione per workstream.
#
#   Fase 0 (sequenziale): phase0-foundations
#   Fase 1 (8 in parallelo): ws-a .. ws-h
#   Fase 2 (3 in parallelo): ws-i, ws-j, ws-k
#
# Ogni fase parte solo quando tutte le sessioni della fase precedente sono
# terminate e i relativi branch/PR sono stati mergiati nel branch base.
# Gli output (prompt renderizzati, request/response API, stato finale, indice
# TSV) vengono salvati in migrate/sessions/<RUN_ID>/.
#
# Requisiti: bash >= 3.2, curl, jq; opzionali git e gh per il controllo merge.
# Variabili d'ambiente:
#   DEVIN_API_KEY      (obbligatoria, tranne in --dry-run)
#   DEVIN_ORG_ID       se impostata usa l'API v3 per-organizzazione, altrimenti v1
#   DEVIN_API_BASE     default https://api.devin.ai
#   DEVIN_PLAYBOOK_ID  opzionale, playbook da allegare a ogni sessione
#   DEVIN_MAX_ACU      opzionale, limite ACU per sessione
#   REPO               default COG-GTM/Agazzi-aws-mainframe-modernization-carddemo
#   BASE_BRANCH        default main
#   POLL_INTERVAL      secondi tra due poll di stato (default 60)
#   PHASE_TIMEOUT      secondi massimi di attesa per fase (default 28800 = 8h)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROMPTS_DIR="$SCRIPT_DIR/prompts"
SESSIONS_ROOT="$SCRIPT_DIR/sessions"

REPO="${REPO:-COG-GTM/Agazzi-aws-mainframe-modernization-carddemo}"
BASE_BRANCH="${BASE_BRANCH:-main}"
DEVIN_API_BASE="${DEVIN_API_BASE:-https://api.devin.ai}"
POLL_INTERVAL="${POLL_INTERVAL:-60}"
PHASE_TIMEOUT="${PHASE_TIMEOUT:-28800}"
BRANCH_PREFIX="migrate"

PHASE0_WS="phase0-foundations"
PHASE1_WS="ws-a-auth ws-b-account ws-c-card ws-d-customer ws-e-transactions ws-f-batch-posting ws-g-batch-interest ws-h-reports"
PHASE2_WS="ws-i-ui ws-j-integration ws-k-data-migration"

TERMINAL_STATUSES="finished blocked expired stopped suspended error exit"

PHASES="0 1 2"
ONLY=""
DRY_RUN=0
WAIT=1
ASSUME_YES=0
SKIP_MERGE_CHECK=0
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"

usage() {
  cat <<USAGE
Uso: $(basename "$0") [opzioni]

  --phase N[,M]        esegue solo le fasi indicate (0, 1, 2). Default: 0,1,2
  --only WS[,WS]       limita ai workstream indicati (es. ws-a-auth,ws-c-card)
  --dry-run            renderizza prompt e payload in migrate/sessions/ senza chiamare l'API
  --no-wait            lancia le sessioni e termina senza attendere (solo con una fase)
  --yes                non chiede conferma interattiva tra una fase e l'altra
  --skip-merge-check   non verifica che i branch della fase precedente siano mergiati
  --run-id ID          riusa/forza l'ID della run (cartella in migrate/sessions/)
  --list               stampa il piano fasi/workstream ed esce
  -h, --help           mostra questo aiuto

Esempi:
  $(basename "$0") --dry-run
  DEVIN_API_KEY=cog_... $(basename "$0")
  DEVIN_API_KEY=cog_... $(basename "$0") --phase 1 --only ws-a-auth,ws-b-account
USAGE
}

log() {
  local msg
  msg="[$(date -u +%H:%M:%SZ)] $*"
  echo "$msg" >&2
  if [ -n "${RUN_DIR:-}" ] && [ -d "$RUN_DIR" ]; then
    echo "$msg" >>"$RUN_DIR/launch.log"
  fi
}

die() { log "ERRORE: $*"; exit 1; }

phase_workstreams() {
  case "$1" in
    0) echo "$PHASE0_WS" ;;
    1) echo "$PHASE1_WS" ;;
    2) echo "$PHASE2_WS" ;;
    *) die "fase sconosciuta: $1" ;;
  esac
}

contains() {
  local needle="$1"; shift
  local item
  for item in "$@"; do [ "$item" = "$needle" ] && return 0; done
  return 1
}

selected_workstreams() {
  local phase="$1" ws out="" only_list
  only_list="$(echo "$ONLY" | tr ',' ' ')"
  for ws in $(phase_workstreams "$phase"); do
    # shellcheck disable=SC2086
    if [ -z "$ONLY" ] || contains "$ws" $only_list; then
      out="$out $ws"
    fi
  done
  echo "$out"
}

print_plan() {
  local p
  for p in 0 1 2; do
    echo "Fase $p:"
    for ws in $(phase_workstreams "$p"); do
      echo "  - $ws  (prompt: prompts/$ws.md, branch: $BRANCH_PREFIX/$ws)"
    done
  done
}

parse_args() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --phase) PHASES="$(echo "${2:?--phase richiede un valore}" | tr ',' ' ')"; shift 2 ;;
      --only) ONLY="${2:?--only richiede un valore}"; shift 2 ;;
      --dry-run) DRY_RUN=1; shift ;;
      --no-wait) WAIT=0; shift ;;
      --yes|-y) ASSUME_YES=1; shift ;;
      --skip-merge-check) SKIP_MERGE_CHECK=1; shift ;;
      --run-id) RUN_ID="${2:?--run-id richiede un valore}"; shift 2 ;;
      --list) print_plan; exit 0 ;;
      -h|--help) usage; exit 0 ;;
      *) usage >&2; die "opzione sconosciuta: $1" ;;
    esac
  done

  local p ws all=""
  for p in $PHASES; do
    case "$p" in 0|1|2) ;; *) die "fase non valida: $p (ammesse 0, 1, 2)" ;; esac
    all="$all $(phase_workstreams "$p")"
  done
  if [ -n "$ONLY" ]; then
    for ws in $(echo "$ONLY" | tr ',' ' '); do
      # shellcheck disable=SC2086
      contains "$ws" $all || die "workstream '$ws' non appartiene alle fasi selezionate ($PHASES)"
    done
  fi
  if [ "$WAIT" -eq 0 ] && [ "$(echo "$PHASES" | wc -w)" -gt 1 ]; then
    die "--no-wait richiede una sola fase (--phase N): le fasi successive dipendono dal completamento"
  fi
}

check_requirements() {
  command -v jq >/dev/null || die "jq non trovato"
  command -v curl >/dev/null || die "curl non trovato"
  local p ws
  for p in $PHASES; do
    for ws in $(selected_workstreams "$p"); do
      [ -f "$PROMPTS_DIR/$ws.md" ] || die "prompt mancante: $PROMPTS_DIR/$ws.md"
    done
  done
  if [ "$DRY_RUN" -eq 0 ] && [ -z "${DEVIN_API_KEY:-}" ]; then
    die "DEVIN_API_KEY non impostata (usa --dry-run per provare senza API)"
  fi
}

# ---------------------------------------------------------------- API Devin

api_create_url() {
  if [ -n "${DEVIN_ORG_ID:-}" ]; then
    echo "$DEVIN_API_BASE/v3/organizations/$DEVIN_ORG_ID/sessions"
  else
    echo "$DEVIN_API_BASE/v1/sessions"
  fi
}

api_get_url() {
  if [ -n "${DEVIN_ORG_ID:-}" ]; then
    echo "$DEVIN_API_BASE/v3/organizations/$DEVIN_ORG_ID/sessions/$1"
  else
    echo "$DEVIN_API_BASE/v1/session/$1"
  fi
}

# Stampa su stdout il body; ritorna !=0 se HTTP non 2xx.
api_call() {
  local method="$1" url="$2" body_file="${3:-}" out code
  out="$(mktemp)"
  if [ -n "$body_file" ]; then
    code="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $DEVIN_API_KEY" \
      -H "Content-Type: application/json" \
      --data-binary "@$body_file")" || code="000"
  else
    code="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $DEVIN_API_KEY")" || code="000"
  fi
  cat "$out"
  rm -f "$out"
  case "$code" in 2??) return 0 ;; *) echo "HTTP $code" >&2; return 1 ;; esac
}

json_session_id() { jq -r '.session_id // .devin_id // .id // empty' "$1"; }
json_session_url() { jq -r '.url // empty' "$1"; }
json_status() { jq -r '(.status_enum // .status_detail // .status // "unknown") | ascii_downcase' "$1"; }
json_pr_url() {
  jq -r '(.pull_request.url // (.pull_requests // [])[0].pr_url // (.pull_requests // [])[0].url // empty)' "$1"
}

# ------------------------------------------------------------- rendering

branch_for() { echo "$BRANCH_PREFIX/$1"; }

render_prompt() {
  local phase="$1" ws="$2" peers="$3" dest="$4" prev=""
  if [ "$phase" -gt 0 ]; then
    prev="$(phase_workstreams $((phase - 1)) | sed 's/^ *//')"
  fi
  {
    echo "# Contesto della sessione (generato da migrate/launch.sh)"
    echo
    echo "- Repository: \`$REPO\`"
    echo "- Run ID: \`$RUN_ID\`"
    echo "- Fase: $phase  |  Workstream: \`$ws\`"
    echo "- Branch base: \`$BASE_BRANCH\` (parti dall'ultimo commit di \`origin/$BASE_BRANCH\`)"
    echo "- Branch da creare (nome esatto): \`$(branch_for "$ws")\`"
    echo "- PR da aprire verso: \`$BASE_BRANCH\` (non fare merge)"
    if [ -n "$prev" ]; then
      echo "- Workstream gia' mergiati in \`$BASE_BRANCH\` (fase $((phase - 1))): $prev"
    fi
    if [ -n "$peers" ]; then
      echo "- Workstream in esecuzione in parallelo nella stessa fase: $peers"
      echo "  Non modificare i loro moduli/package: lavorano su branch separati."
    fi
    echo
    echo "---"
    echo
    cat "$PROMPTS_DIR/$ws.md"
  } >"$dest"
}

build_payload() {
  local phase="$1" ws="$2" prompt_file="$3" dest="$4"
  jq -n \
    --rawfile prompt "$prompt_file" \
    --arg title "[carddemo-migrate] fase $phase - $ws" \
    --arg ws "$ws" \
    --arg phase "phase-$phase" \
    --arg run "run-$RUN_ID" \
    --arg playbook "${DEVIN_PLAYBOOK_ID:-}" \
    --arg max_acu "${DEVIN_MAX_ACU:-}" \
    '{prompt: $prompt, title: $title, tags: ["carddemo-migrate", $phase, $ws, $run]}
     + (if $playbook != "" then {playbook_id: $playbook} else {} end)
     + (if $max_acu != "" then {max_acu_limit: ($max_acu | tonumber)} else {} end)' \
    >"$dest"
}

# ------------------------------------------------------------- sessioni

launch_one() {
  local phase="$1" ws="$2" peers="$3"
  local prompt="$RUN_DIR/$ws.prompt.md" req="$RUN_DIR/$ws.request.json" resp="$RUN_DIR/$ws.response.json"
  render_prompt "$phase" "$ws" "$peers" "$prompt"
  build_payload "$phase" "$ws" "$prompt" "$req"
  if [ "$DRY_RUN" -eq 1 ]; then
    jq -n --arg ws "$ws" '{session_id: ("dry-run-" + $ws), url: "", status: "dry-run"}' >"$resp"
    log "[dry-run] $ws -> $prompt"
    return 0
  fi
  if api_call POST "$(api_create_url)" "$req" >"$resp" 2>"$RUN_DIR/$ws.error.log"; then
    log "avviata $ws: $(json_session_url "$resp") ($(json_session_id "$resp"))"
  else
    log "creazione sessione fallita per $ws: $(cat "$RUN_DIR/$ws.error.log") $(head -c 500 "$resp")"
    return 1
  fi
}

launch_phase() {
  local phase="$1" wss="$2" ws peers pids="" failed=0 pid
  log "=== Fase $phase: avvio di $(echo "$wss" | wc -w | tr -d ' ') sessione/i:$wss"
  for ws in $wss; do
    peers="$( (echo "$wss" | tr ' ' '\n' | grep -v -x -e "$ws" -e '' || true) | tr '\n' ' ' | sed 's/ $//')"
    launch_one "$phase" "$ws" "$peers" &
    pids="$pids $!"
  done
  for pid in $pids; do
    wait "$pid" || failed=1
  done
  for ws in $wss; do
    record_index "$phase" "$ws" "$RUN_DIR/$ws.response.json" launched
  done
  [ "$failed" -eq 0 ] || die "una o piu' sessioni della fase $phase non sono state create (vedi $RUN_DIR/*.error.log)"
}

record_index() {
  local phase="$1" ws="$2" file="$3" default_status="${4:-unknown}" id url status pr
  id="$(json_session_id "$file" 2>/dev/null || true)"
  url="$(json_session_url "$file" 2>/dev/null || true)"
  status="$(json_status "$file" 2>/dev/null || echo unknown)"
  if [ "$status" = "unknown" ]; then
    if [ -n "$id" ]; then status="$default_status"; else status="create_failed"; fi
  fi
  pr="$(json_pr_url "$file" 2>/dev/null || true)"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$phase" "$ws" "$(branch_for "$ws")" "${id:--}" "${url:--}" "$status" "${pr:--}" \
    >>"$RUN_DIR/sessions.tsv"
}

is_terminal() {
  # shellcheck disable=SC2086
  contains "$1" $TERMINAL_STATUSES
}

wait_phase() {
  local phase="$1" wss="$2" start ws id status pending
  start="$(date +%s)"
  log "attendo il completamento della fase $phase (poll ogni ${POLL_INTERVAL}s, timeout ${PHASE_TIMEOUT}s)"
  while :; do
    pending=""
    for ws in $wss; do
      id="$(json_session_id "$RUN_DIR/$ws.response.json")"
      if api_call GET "$(api_get_url "$id")" >"$RUN_DIR/$ws.status.json.tmp" 2>/dev/null; then
        mv "$RUN_DIR/$ws.status.json.tmp" "$RUN_DIR/$ws.status.json"
      else
        rm -f "$RUN_DIR/$ws.status.json.tmp"
      fi
      status="unknown"
      [ -f "$RUN_DIR/$ws.status.json" ] && status="$(json_status "$RUN_DIR/$ws.status.json")"
      is_terminal "$status" || pending="$pending $ws($status)"
    done
    if [ -z "$pending" ]; then
      break
    fi
    if [ $(( $(date +%s) - start )) -ge "$PHASE_TIMEOUT" ]; then
      die "timeout fase $phase, ancora in corso:$pending"
    fi
    log "fase $phase in corso:$pending"
    sleep "$POLL_INTERVAL"
  done
  for ws in $wss; do
    record_index "$phase" "$ws" "$RUN_DIR/$ws.status.json"
    log "fase $phase / $ws: stato=$(json_status "$RUN_DIR/$ws.status.json") PR=$(json_pr_url "$RUN_DIR/$ws.status.json")"
  done
}

# ------------------------------------------------------- gate tra le fasi

branch_merged() {
  local branch="$1" state
  if command -v gh >/dev/null 2>&1; then
    state="$(gh pr list --repo "$REPO" --head "$branch" --base "$BASE_BRANCH" --state merged \
      --json number -q 'length' 2>/dev/null || echo "")"
    if [ -n "$state" ]; then
      [ "$state" -gt 0 ] && return 0 || return 1
    fi
  fi
  if command -v git >/dev/null 2>&1 && git -C "$SCRIPT_DIR" rev-parse --git-dir >/dev/null 2>&1; then
    git -C "$SCRIPT_DIR" fetch -q origin "$BASE_BRANCH" "$branch" 2>/dev/null || return 1
    git -C "$SCRIPT_DIR" merge-base --is-ancestor "origin/$branch" "origin/$BASE_BRANCH" 2>/dev/null
    return $?
  fi
  return 1
}

gate_before_phase() {
  local phase="$1" prev ws missing answer
  [ "$phase" -eq 0 ] && return 0
  [ "$DRY_RUN" -eq 1 ] && return 0
  [ "$SKIP_MERGE_CHECK" -eq 1 ] && { log "controllo merge saltato (--skip-merge-check)"; return 0; }
  prev=$((phase - 1))
  while :; do
    missing=""
    for ws in $(phase_workstreams "$prev"); do
      branch_merged "$(branch_for "$ws")" || missing="$missing $(branch_for "$ws")"
    done
    [ -z "$missing" ] && { log "fase $prev: tutti i branch risultano mergiati in $BASE_BRANCH"; return 0; }
    log "prima della fase $phase vanno mergiati in $BASE_BRANCH:$missing"
    if [ "$ASSUME_YES" -eq 1 ] || [ ! -t 0 ]; then
      log "attendo ${POLL_INTERVAL}s e ricontrollo (Ctrl-C per interrompere, --skip-merge-check per forzare)"
      sleep "$POLL_INTERVAL"
    else
      printf 'Premi Invio dopo il merge per ricontrollare, "s" per saltare il controllo, "q" per uscire: ' >&2
      read -r answer
      case "$answer" in
        s|S) log "controllo merge saltato dall'utente"; return 0 ;;
        q|Q) die "interrotto dall'utente prima della fase $phase" ;;
      esac
    fi
  done
}

# ------------------------------------------------------------------ main

main() {
  parse_args "$@"
  check_requirements

  RUN_DIR="$SESSIONS_ROOT/$RUN_ID"
  mkdir -p "$RUN_DIR"
  ln -sfn "$RUN_ID" "$SESSIONS_ROOT/latest"
  [ -f "$RUN_DIR/sessions.tsv" ] || printf 'phase\tworkstream\tbranch\tsession_id\turl\tstatus\tpr_url\n' >"$RUN_DIR/sessions.tsv"

  log "run $RUN_ID | repo $REPO | base $BASE_BRANCH | fasi: $PHASES | dry-run=$DRY_RUN | api=$( [ -n "${DEVIN_ORG_ID:-}" ] && echo v3 || echo v1 )"

  local phase wss
  for phase in $PHASES; do
    wss="$(selected_workstreams "$phase")"
    if [ -z "${wss// /}" ]; then
      log "fase $phase: nessun workstream selezionato, salto"
      continue
    fi
    gate_before_phase "$phase"
    launch_phase "$phase" "$wss"
    if [ "$DRY_RUN" -eq 1 ] || [ "$WAIT" -eq 0 ]; then
      continue
    fi
    wait_phase "$phase" "$wss"
  done

  log "fatto. Indice sessioni: $RUN_DIR/sessions.tsv"
}

main "$@"
