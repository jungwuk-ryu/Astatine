#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd -P)

if ! command -v node >/dev/null 2>&1; then
  echo "error: node 18+ is required to run the benchmark automation" >&2
  exit 127
fi

NODE_MAJOR=$(node -e "process.stdout.write(process.versions.node.split('.')[0])")
if [ "$NODE_MAJOR" -lt 18 ]; then
  echo "error: node 18+ is required; found $(node --version)" >&2
  exit 1
fi

exec node "$SCRIPT_DIR/run-benchmarks.mjs" "$@"
