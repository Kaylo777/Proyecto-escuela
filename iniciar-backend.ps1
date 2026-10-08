# ============================================================
#  Inicia todo el backend de microservicios desde la raiz del
#  proyecto. Al ejecutarse desde aqui, las banderas jdbc:h2:file:./data/*
#  apuntan a la carpeta data\ de la raiz (bases H2 reutilizables).
# ============================================================

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# Orden importante: registry y config-server son la base. Si arrancan los
# microservicios antes, no encuentran la configuracion centralizada (caen en los
# valores de respaldo de su application.yml) y el gateway no puede resolver las
# rutas lb:// hasta que Eureka los registra.
$orden = @(
    @{ nombre = 'registry';      jar = 'registry-0.0.1-SNAPSHOT.jar';       puerto = 8761 },
    @{ nombre = 'config-server'; jar = 'config-server-0.0.1-SNAPSHOT.jar';  puerto = 8888 }
)

$logDir = Join-Path $root 'backend\logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host "Levantando backend. Requiere RabbitMQ activo:  docker compose up -d" -ForegroundColor Yellow

# El Config Server usa el backend "git": necesita que exista el repositorio de
# configuracion. Se crea o se sincroniza antes de arrancar nada, porque sin el los
# servicios caen en los valores de respaldo de su application.yml y arrancan sin
# secretos ni token de servicio.
$scriptConfigRepo = Join-Path $root 'iniciar-config-repo.ps1'
if (Test-Path -LiteralPath $scriptConfigRepo) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $scriptConfigRepo | Out-Null
}

# El backend "git" del Config Server necesita una URI absoluta al repositorio:
# JGit no resuelve bien rutas relativas con espacios ("Default Project"), y la
# carpeta de trabajo del proceso no es necesariamente la del proyecto. Se calcula
# aca con la ruta real y se pasa por variable de entorno.
#
# Para usar un repositorio remoto de GitHub en vez del local, asignar antes:
#   $env:CONFIG_REPO_URI = 'https://github.com/usuario/escuela-config.git'
if (-not $env:CONFIG_REPO_URI) {
    $rutaRepo = (Join-Path $root 'config-repo') -replace '\\', '/'
    $env:CONFIG_REPO_URI = 'file:///' + ($rutaRepo -replace ' ', '%20')
}

# Java 17 o superior es obligatorio (Spring Boot 4). Se avisa temprano en lugar de
# dejar que Maven fallen con un error de class file version.
# "java -version" escribe en stderr: con $ErrorActionPreference='Stop' eso seria un
# error terminal, asi que se relaja solo durante la comprobacion.
$preferenciaPrevia = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersionOutput = (& java -version 2>&1) -join ' '
$ErrorActionPreference = $preferenciaPrevia
if ($javaVersionOutput -match 'version "(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 17) {
        Write-Warning "Java detectado: $($Matches[1]). El proyecto necesita Java 17 o superior."
        Write-Warning "  Si JAVA_HOME apunta a Java 11, actualizalo o corre el script desde una terminal nueva."
    }
}

# RabbitMQ es dependencia dura de alumnos y administracion: sin broker, los
# listeners quedan reconectando en loop y los servicios tardan mucho mas en
# quedar healthy. Se comprueba antes de arrancar nada.
if (-not (Test-NetConnection -ComputerName localhost -Port 5672 -InformationLevel Quiet -WarningAction SilentlyContinue)) {
    Write-Warning "RabbitMQ no responde en el puerto 5672."
    Write-Warning "  Ejecuta 'docker compose up -d' y volve a correr este script."
}

function Levantar($s) {
    $jarPath = Join-Path $root "backend\$($s.nombre)\target\$($s.jar)"
    if (-not (Test-Path $jarPath)) {
        Write-Warning "No se encontro el jar de $($s.nombre). Ejecuta:  backend\$($s.nombre)\mvnw.cmd package -DskipTests"
        return
    }
    # Comillas en la ruta: sin ellas java no encuentra el jar si la carpeta
    # del proyecto tiene espacios (ej. "Default Project").
    Start-Process java -ArgumentList "-jar `"$jarPath`"" -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir "$($s.nombre).log") `
        -RedirectStandardError  (Join-Path $logDir "$($s.nombre).err") `
        -WindowStyle Hidden
    Write-Host "  -> $($s.nombre) iniciado" -ForegroundColor Green
}

function Esperar($puerto, $segundos) {
    for ($i = 0; $i -lt $segundos; $i++) {
        if (Test-NetConnection -ComputerName localhost -Port $puerto -InformationLevel Quiet -WarningAction SilentlyContinue) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

foreach ($s in $orden) {
    Levantar $s
    if (-not (Esperar $s.puerto 90)) {
        Write-Warning "  $($s.nombre) no respondio en el puerto $($s.puerto). Revisá backend\logs\$($s.nombre).log"
    }
}

# A partir de aqui el resto de los servicios. Tambien se espera a que abran su
# puerto: sin esto, el gateway puede arrancar antes de que alumnos este
# registrado en Eureka y las primeras llamadas a lb://alumnos fallan.
foreach ($s in @(
    @{ nombre = 'auth-service';   jar = 'auth-service-0.0.1-SNAPSHOT.jar';   puerto = 8100 },
    @{ nombre = 'alumnos';        jar = 'alumnos-0.0.1-SNAPSHOT.jar';        puerto = 8101 },
    @{ nombre = 'administracion'; jar = 'administracion-0.0.1-SNAPSHOT.jar'; puerto = 8102 },
    @{ nombre = 'gateway';        jar = 'gateway-0.0.1-SNAPSHOT.jar';        puerto = 8080 },
    @{ nombre = 'admin-server';   jar = 'admin-server-0.0.1-SNAPSHOT.jar';   puerto = 9090 }
)) {
    Levantar $s
    if (-not (Esperar $s.puerto 120)) {
        Write-Warning "  $($s.nombre) no respondio en el puerto $($s.puerto). Revisá backend\logs\$($s.nombre).log"
    }
}

Write-Host ""
Write-Host "URLs:"
Write-Host "  Eureka (Registry):  http://localhost:8761"
Write-Host "  Config Server:      http://localhost:8888"
Write-Host "  API Gateway:        http://localhost:8080"
Write-Host "  Auth Service:       http://localhost:8100"
Write-Host "  RabbitMQ UI:        http://localhost:15672  (guest/guest)"
Write-Host "  Spring Boot Admin:  http://localhost:9090"
Write-Host ""
Write-Host "  Alumnos y administracion NO aceptan acceso directo: exigen el token de"
Write-Host "  servicio. Solo se acceden a traves del gateway (puerto 8080)."
Write-Host "Logs en: backend\logs"