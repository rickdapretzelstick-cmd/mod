#!/bin/sh
cd "$(dirname "$0")"
exec python3 tools/r6_batch.py "$@"
