#!/usr/bin/env bash
# Compara el rendimiento con 1 réplica y con 3 réplicas de la API usando k6.
# Uso:  ./scripts/stress-compare.sh                   (escenarios "stress" y "saturacion")
#       PROFILES=smoke ./scripts/stress-compare.sh      (prueba corta)
set -euo pipefail
cd "$(dirname "$0")/.."

PROFILES="${PROFILES:-stress saturacion}"
mkdir -p docs/resultados
chmod 777 docs/resultados 2>/dev/null || true   # k6 corre con un usuario sin privilegios

echo "==> Levantando el sistema"
docker compose up -d --build --wait

for REPLICAS in 1 3; do
  echo "==> Escalando la API a ${REPLICAS} réplica(s)"
  docker compose up -d --scale api="${REPLICAS}" --no-recreate --wait api
  sleep 10   # Nginx vuelve a resolver el DNS cada 5 s

  for PROFILE in ${PROFILES}; do
    echo "==> Ejecutando k6 (${PROFILE}) contra ${REPLICAS} réplica(s)"
    docker compose --profile stress run --rm \
      -e REPLICAS="${REPLICAS}" -e PROFILE="${PROFILE}" \
      k6 run /scripts/stress.js || echo "(k6 terminó con umbrales no cumplidos; el resultado se guarda igual)"
    sleep 5
  done
done

echo "==> Resultados guardados en docs/resultados/"
ls -1 docs/resultados
