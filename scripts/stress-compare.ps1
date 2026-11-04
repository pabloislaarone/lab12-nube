# Compara el rendimiento con 1 réplica y con 3 réplicas de la API usando k6 (Windows / PowerShell).
# Uso:  .\scripts\stress-compare.ps1               (prueba completa, ~4 min por escenario)
#       .\scripts\stress-compare.ps1 -Perfil smoke (prueba corta)
param([string]$Perfil = "stress")

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force -Path "docs/resultados" | Out-Null

Write-Host "==> Levantando el sistema" -ForegroundColor Cyan
docker compose up -d --build --wait

foreach ($replicas in 1, 3) {
    Write-Host "==> Escalando la API a $replicas replica(s)" -ForegroundColor Cyan
    docker compose up -d --scale api=$replicas --no-recreate --wait api
    Start-Sleep -Seconds 10

    Write-Host "==> Ejecutando k6 ($Perfil) contra $replicas replica(s)" -ForegroundColor Cyan
    docker compose --profile stress run --rm -e REPLICAS=$replicas -e PROFILE=$Perfil k6 run /scripts/stress.js
}

Write-Host "==> Resultados guardados en docs/resultados/" -ForegroundColor Green
Get-ChildItem docs/resultados
