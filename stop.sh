#!/usr/bin/env bash
# Ferma i processi avviati da launch.sh
cd "$(dirname "$0")"
if [ -f logs/pids ]; then
    kill $(cat logs/pids) 2>/dev/null || true
    rm logs/pids
    echo "Processi fermati"
else
    echo "Nessun processo da fermare"
fi
