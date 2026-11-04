#!/usr/bin/env bash
# Hace N peticiones al balanceador y cuenta qué réplica respondió cada una.
# Uso: ./scripts/balanceo.sh [cantidad] [url]
set -euo pipefail
N="${1:-30}"
URL="${2:-http://localhost:8080}"

for _ in $(seq 1 "$N"); do
  curl -s -D - -o /dev/null "$URL/api/info" | grep -i '^x-instance-id' | awk '{print $2}' | tr -d '\r'
done | sort | uniq -c | awk '{printf "Réplica %-14s atendió %s peticiones\n", $2, $1}'
