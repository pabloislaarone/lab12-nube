# Compara el rendimiento con 1 réplica y con 3 réplicas de la API usando k6 (Windows / PowerShell).
# Uso:  .\scripts\stress-compare.ps1                       (escenarios "stress" y "saturacion")
#       .\scripts\stress-compare.ps1 -Perfiles smoke       (prueba corta)
param([string[]]$Perfiles = @("stress", "saturacion"))

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force -Path "docs/resultados" | Out-Null

Write-Host "==> Levantando el sistema" -ForegroundColor Cyan
docker compose up -d --build --wait

foreach ($replicas in 1, 3) {
    Write-Host "==> Escalando la API a $replicas replica(s)" -ForegroundColor Cyan
    docker compose up -d --scale api=$replicas --no-recreate --wait api
    Start-Sleep -Seconds 10

    foreach ($perfil in $Perfiles) {
        Write-Host "==> Ejecutando k6 ($perfil) contra $replicas replica(s)" -ForegroundColor Cyan
        docker compose --profile stress run --rm -e REPLICAS=$replicas -e PROFILE=$perfil k6 run /scripts/stress.js
        Start-Sleep -Seconds 5
    }
}

Write-Host "==> Resultados guardados en docs/resultados/" -ForegroundColor Green
Get-ChildItem docs/resultados
