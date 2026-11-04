# Hace N peticiones al balanceador y cuenta qué réplica respondió cada una (Windows / PowerShell).
# Uso: .\scripts\balanceo.ps1 [-N 30] [-Url http://localhost:8080]
param([int]$N = 30, [string]$Url = "http://localhost:8080")

$conteo = @{}
for ($i = 0; $i -lt $N; $i++) {
    $res = Invoke-WebRequest -Uri "$Url/api/info" -UseBasicParsing
    $replica = $res.Headers["X-Instance-Id"]
    if ($replica -is [array]) { $replica = $replica[0] }
    $conteo[$replica] = 1 + [int]$conteo[$replica]
}
$conteo.GetEnumerator() | Sort-Object Name | ForEach-Object {
    Write-Host ("Replica {0,-14} atendio {1} peticiones" -f $_.Name, $_.Value)
}
