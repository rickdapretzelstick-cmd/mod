#!/bin/bash
# bench.sh TOOLS_DIR LABEL [CACHE FIRST LAST STEP] -- one benchmark at a time on this machine (a lock), so timings are fair.
B=$(cd "$(dirname "$0")" && pwd)
TOOLS=$1; LABEL=$2; CACHE=${3:-left}; FIRST=${4:-526}; LAST=${5:-546}; STEP=${6:-1}
OUT=$B/runs/$LABEL.json
exec 9>$B/bench.lock
flock 9
cd $B/runs && python3 -u -W ignore $B/run_candidate.py "$TOOLS" $CACHE $FIRST $LAST $STEP $OUT && python3 -u -W ignore $B/score.py $OUT
