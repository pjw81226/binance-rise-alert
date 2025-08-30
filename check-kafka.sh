set -euo pipefail

BOOTSTRAP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --bootstrap) BOOTSTRAP="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[check-kafka] 알 수 없는 옵션: $1"; usage; exit 1 ;;
  esac
done

if [[ -z "$BOOTSTRAP" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    BOOTSTRAP="host.docker.internal:9094"
  else
    BOOTSTRAP="localhost:9094"
  fi
fi

HOST="${BOOTSTRAP%%:*}"
PORT="${BOOTSTRAP##*:}"

echo "[check-kafka] Checking $HOST:$PORT ..."
for i in {1..15}; do
  if command -v nc >/dev/null 2>&1; then
    if nc -z "$HOST" "$PORT"; then
      echo "[check-kafka] OK"
      exit 0
    fi
  else
    if (echo >"/dev/tcp/$HOST/$PORT") >/dev/null 2>&1; then
      echo "[check-kafka] OK"
      exit 0
    fi
  fi
  echo "[check-kafka] not ready yet... retry=$i"
  sleep 2
done

echo "[check-kafka] FAIL: $HOST:$PORT not reachable."
exit 1