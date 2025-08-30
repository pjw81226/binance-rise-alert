set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR/producer"

if [[ ! -f "./gradlew" ]]; then
  "$ROOT_DIR/bootstrap.sh"
fi

./gradlew --no-daemon clean build -x test