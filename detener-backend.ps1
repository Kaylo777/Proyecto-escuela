# ============================================================
#  Detiene todos los microservicios del backend.
# ============================================================

$procesos = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match '\\backend\\' }

if ($procesos) {
    $procesos | ForEach-Object {
        Write-Host "Deteniendo PID $($_.ProcessId): $($_.CommandLine -split '-jar ')[1]" -ForegroundColor Yellow
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "No hay microservicios corriendo." -ForegroundColor Green
}

# Opcional: detener tambien RabbitMQ
$resp = Read-Host "¿Detener tambien RabbitMQ? (s/n)"
if ($resp -match '^[sS]') {
    docker compose down
}