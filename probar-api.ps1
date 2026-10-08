# Bateria de pruebas de la API. Verifica los cinco puntos de arquitectura que
# corrigio el docente, ademas del comportamiento funcional.
#
#   .\probar-api.ps1
#
# Requiere el backend arriba (.\iniciar-backend.ps1). Sale con codigo 1 si alguna
# verificacion falla, para poder encadenarlo en un pipeline.

$ErrorActionPreference = 'Continue'
$gateway = 'http://localhost:8080'

$script:fallos = 0
$script:total = 0

function Probar($nombre, $esperado, $obtenido) {
    $script:total++
    if ("$obtenido" -eq "$esperado") {
        Write-Host ("  OK   {0,-42} {1}" -f $nombre, $obtenido) -ForegroundColor Green
    }
    else {
        Write-Host ("  FALLO {0,-41} esperado={1} obtenido={2}" -f $nombre, $esperado, $obtenido) -ForegroundColor Red
        $script:fallos++
    }
}

function Estado($metodo, $uri, $headers, $cuerpo) {
    $p = @{ Uri = $uri; Method = $metodo; Headers = $headers; TimeoutSec = 30; UseBasicParsing = $true }
    if ($cuerpo) { $p.Body = $cuerpo; $p.ContentType = 'application/json' }
    try { return (Invoke-WebRequest @p).StatusCode }
    catch { if ($_.Exception.Response) { return $_.Exception.Response.StatusCode.value__ }; return 'sin-respuesta' }
}

function ObtenerJson($uri, $headers) {
    try { return Invoke-RestMethod -Uri $uri -Headers $headers -TimeoutSec 30 }
    catch { return $null }
}

Write-Host ""
Write-Host "1. Autenticacion (delega en el auth-service)" -ForegroundColor Cyan
$loginBody = '{"username":"admin","password":"admin123"}'
$admin = Invoke-RestMethod -Uri "$gateway/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
$userBody = '{"username":"user","password":"user123"}'
$user = Invoke-RestMethod -Uri "$gateway/auth/login" -Method Post -ContentType 'application/json' -Body $userBody -TimeoutSec 30

Probar "login admin devuelve access token" 'si' $(if ($admin.accessToken) { 'si' } else { 'no' })
Probar "login admin devuelve refresh token" 'si' $(if ($admin.refreshToken) { 'si' } else { 'no' })
Probar "login admin rol ADMIN" 'ADMIN' $admin.roles[0]
Probar "login user rol USER" 'USER' $user.roles[0]
Probar "password incorrecta" 401 (Estado 'POST' "$gateway/auth/login" @{} '{"username":"admin","password":"mala"}')
Probar "usuario inexistente" 401 (Estado 'POST' "$gateway/auth/login" @{} '{"username":"fantasma","password":"x"}')

$AA = @{ Authorization = "Bearer $($admin.accessToken)" }
$AU = @{ Authorization = "Bearer $($user.accessToken)" }
$MO = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('monitor:monitor123')) }

Write-Host ""
Write-Host "2. Perfil y refresh" -ForegroundColor Cyan
$me = ObtenerJson "$gateway/auth/me" $AA
Probar "GET /auth/me devuelve admin" 'admin' $me.username
Probar "GET /auth/me sin token" 401 (Estado 'GET' "$gateway/auth/me" @{} $null)
Probar "refresh valido" 200 (Estado 'POST' "$gateway/auth/refresh" @{} (@{ refreshToken = $admin.refreshToken } | ConvertTo-Json))
Probar "refresh invalido" 401 (Estado 'POST' "$gateway/auth/refresh" @{} '{"refreshToken":"abc"}')
Probar "access token no sirve para refresh" 401 (Estado 'POST' "$gateway/auth/refresh" @{} (@{ refreshToken = $admin.accessToken } | ConvertTo-Json))
Probar "refresh como Bearer en la API" 401 (Estado 'GET' "$gateway/api/alumnos" @{ Authorization = "Bearer $($admin.refreshToken)" } $null)

Write-Host ""
Write-Host "3. Matriz de roles, ahora aplicada SOLO por el gateway" -ForegroundColor Cyan
$alumno = '{"nombre":"Carla","apellido":"Nunez","dni":"41555666","email":"carla@colegio.edu","curso":"6toB","fechaNacimiento":"2011-01-10"}'
$docente = '{"nombre":"Pablo","apellido":"Vega","dni":"30777788","email":"pablo@colegio.edu","especialidad":"Fisica"}'
Probar "GET alumnos admin" 200 (Estado 'GET' "$gateway/api/alumnos" $AA $null)
Probar "GET alumnos user" 200 (Estado 'GET' "$gateway/api/alumnos" $AU $null)
Probar "GET alumnos sin token" 401 (Estado 'GET' "$gateway/api/alumnos" @{} $null)
Probar "POST alumnos admin" 201 (Estado 'POST' "$gateway/api/alumnos" $AA $alumno)
Probar "POST alumnos user (bloqueado)" 403 (Estado 'POST' "$gateway/api/alumnos" $AU $alumno)
Probar "PUT alumnos user (bloqueado)" 403 (Estado 'PUT' "$gateway/api/alumnos/1" $AU $alumno)
Probar "DELETE alumnos user (bloqueado)" 403 (Estado 'DELETE' "$gateway/api/alumnos/1" $AU $null)
Probar "POST docente admin" 201 (Estado 'POST' "$gateway/api/administracion" $AA $docente)
Probar "POST docente user (bloqueado)" 403 (Estado 'POST' "$gateway/api/administracion" $AU $docente)

Write-Host ""
Write-Host "4. Token de servicio: acceso directo a los microservicios" -ForegroundColor Cyan
$tokenOk = 'bjVTzQ28dPcdlCLBU+qtKQTauTCViQFGo/aw1KVXbuM='
Probar "alumnos :8101 sin token de servicio" 403 (Estado 'GET' 'http://localhost:8101/api/alumnos' @{} $null)
Probar "alumnos :8101 con token de servicio FALSO" 403 (Estado 'GET' 'http://localhost:8101/api/alumnos' @{ 'X-Service-Token' = 'inventado' } $null)
Probar "administracion :8102 sin token" 403 (Estado 'GET' 'http://localhost:8102/api/administracion' @{} $null)
Probar "auth-service :8100 sin token" 403 (Estado 'POST' 'http://localhost:8100/auth/login' @{} $loginBody)

# Estas dos NO son un agujero: son el diseno funcionando. Los microservicios no
# validan el JWT del usuario (eso lo hace el gateway, y solo el gateway), asi que
# con el token de servicio correcto responden 200 haya o no un Authorization.
# Lo que bloquea el acceso directo es el token de servicio, no el token de usuario.
Probar "alumnos :8101 con token de servicio OK" 200 (Estado 'GET' 'http://localhost:8101/api/alumnos' @{ 'X-Service-Token' = $tokenOk } $null)
Probar "alumnos :8101 ignora Authorization" 200 (Estado 'GET' 'http://localhost:8101/api/alumnos' @{ 'X-Service-Token' = $tokenOk; Authorization = "Bearer $($admin.accessToken)" } $null)

Write-Host ""
Write-Host "5. Aislamiento de la cuenta de monitoreo" -ForegroundColor Cyan
Probar "health publico" 200 (Estado 'GET' "$gateway/actuator/health" @{} $null)
Probar "actuator/env con Basic monitor" 200 (Estado 'GET' "$gateway/actuator/env" $MO $null)
Probar "actuator/env sin credencial" 401 (Estado 'GET' "$gateway/actuator/env" @{} $null)
Probar "Basic monitor no alcanza para /api" 401 (Estado 'GET' "$gateway/api/alumnos" $MO $null)
# Basic no pertenece a la cadena JWT, asi que monitor nunca llega a autorizacion: 401.
Probar "Basic monitor no alcanza para POST" 401 (Estado 'POST' "$gateway/api/alumnos" $MO $alumno)

Write-Host ""
Write-Host "6. DTOs: la entidad no se expone ni se acepta" -ForegroundColor Cyan
Probar "POST sin nombre (validacion @NotBlank)" 400 (Estado 'POST' "$gateway/api/alumnos" $AA '{"apellido":"Nunez"}')
Probar "POST con email invalido" 400 (Estado 'POST' "$gateway/api/alumnos" $AA '{"nombre":"X","apellido":"Y","email":"no-es-mail"}')
Probar "POST con id en el cuerpo (mass assignment)" 201 (Estado 'POST' "$gateway/api/alumnos" $AA '{"id":9999,"nombre":"Ignor","apellido":"Id","dni":"11111111","email":"i@colegio.edu"}')

$lista = ObtenerJson "$gateway/api/alumnos" $AA
$conId9999 = @($lista | Where-Object { $_.id -eq 9999 }).Count
Probar "el id 9999 del cuerpo fue ignorado" '0' $conId9999

Write-Host ""
Write-Host "7. Configuracion servida por el backend git del Config Server" -ForegroundColor Cyan
$cfg = ObtenerJson 'http://localhost:8888/alumnos/default' @{}
$origen = @($cfg.propertySources | ForEach-Object { $_.name }) -join ' | '
Probar "el origen es el repositorio config-repo" 'si' $(if ($origen -match 'config-repo') { 'si' } else { "no -> $origen" })
$appSrc = @($cfg.propertySources | Where-Object { $_.name -match 'application\.yml' })[0]
Probar "viene app.service-token.value" 'si' $(if ($appSrc.source.'app.service-token.value') { 'si' } else { 'no' })
Probar "viene app.jwt.secret" 'si' $(if ($appSrc.source.'app.jwt.secret') { 'si' } else { 'no' })

Write-Host ""
Write-Host "8. RabbitMQ: eventos entre microservicios" -ForegroundColor Cyan
$logAlumnos = 'backend\logs\alumnos.log'
$logAdmin = 'backend\logs\administracion.log'
if ((Test-Path $logAlumnos) -and (Test-Path $logAdmin)) {
    $a = @(Select-String -LiteralPath $logAdmin -Pattern 'Evento recibido' | Select-Object -Last 1)
    $d = @(Select-String -LiteralPath $logAlumnos -Pattern 'Evento recibido' | Select-Object -Last 1)
    Probar "administracion recibio evento de alumno" 'si' $(if ($a.Count -gt 0) { 'si' } else { 'no' })
    Probar "alumnos recibio evento de docente" 'si' $(if ($d.Count -gt 0) { 'si' } else { 'no' })
}
else { Write-Host "  (logs no encontrados, se omite)" -ForegroundColor DarkGray }

Write-Host ""
Write-Host "---------------------------------------------------"
Write-Host ("Total: {0}   Fallos: {1}" -f $script:total, $script:fallos) -ForegroundColor $(if ($script:fallos -eq 0) { 'Green' } else { 'Red' })

if ($script:fallos -gt 0) { exit 1 }
exit 0