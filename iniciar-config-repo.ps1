# Crea (o actualiza) el repositorio Git de configuracion que sirve el Config Server.
#
# Por que un repositorio aparte: con el backend "native", los .yml de configuracion
# se hornean dentro del jar del Config Server. Cambiar una propiedad obligaba a
# recompilar y reiniciar, y no habia historial de cambios. Con el backend "git",
# la configuracion vive en un repo versionado: se edita, se commitea y el Config
# Server la sirve sin recompilar.
#
# El repo se llama config-repo/ y NO se versiona en este repositorio de codigo
# (esta en .gitignore), porque es un repositorio Git independiente, con su propio
# historial. La plantilla versionada esta en config-template/.
#
# Es idempotente: si el repo ya existe, solo sincroniza los archivos que cambiaron.

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$raiz = $PSScriptRoot
$plantilla = Join-Path $raiz 'config-template'
$destino = Join-Path $raiz 'config-repo'

if (-not (Test-Path -LiteralPath $plantilla)) {
    Write-Host "No existe la plantilla config-template. Aborta." -ForegroundColor Red
    exit 1
}

Write-Host "Config Server - repositorio de configuracion" -ForegroundColor Cyan

# ---------------------------------------------------------------- primer arranque
if (-not (Test-Path -LiteralPath (Join-Path $destino '.git'))) {
    Write-Host "  Creando repositorio Git en config-repo\ ..." -ForegroundColor Yellow

    if (Test-Path -LiteralPath $destino) {
        Remove-Item -LiteralPath $destino -Recurse -Force
    }
    New-Item -ItemType Directory -Path $destino -Force | Out-Null

    Copy-Item -Path (Join-Path $plantilla '*') -Destination $destino -Recurse -Force

    Push-Location $destino
    try {
        & git init --quiet --initial-branch=main 2>&1 | Out-Null
        & git add -A
        & git -c user.name='Kaylo777' -c user.email='santinomoreno821@gmail.com' `
            commit --quiet -m 'Configuracion inicial del colegio'
    }
    finally { Pop-Location }

    Write-Host "  Repositorio creado con 1 commit." -ForegroundColor Green
}
else {
    # ------------------------------------------------------------ sincronizar
    Write-Host "  El repositorio ya existe; sincronizando con la plantilla..." -ForegroundColor Yellow

    $cambios = 0
    Get-ChildItem -LiteralPath $plantilla -File | ForEach-Object {
        $origen = $_.FullName
        $dest = Join-Path $destino $_.Name
        $hayCambio = -not (Test-Path -LiteralPath $dest) -or
                     ((Get-FileHash -LiteralPath $origen).Hash -ne (Get-FileHash -LiteralPath $dest).Hash)

        if ($hayCambio) {
            Copy-Item -LiteralPath $origen -Destination $dest -Force
            Write-Host "    actualizado: $($_.Name)" -ForegroundColor DarkGray
            $cambios++
        }
    }

    if ($cambios -gt 0) {
        Push-Location $destino
        try {
            & git add -A
            & git -c user.name='Kaylo777' -c user.email='santinomoreno821@gmail.com' `
                commit --quiet -m "Sincronizacion desde config-template ($cambios archivos)"
            $hash = & git rev-parse --short HEAD
            Write-Host "  $cambios archivo(s) commiteados (rev $hash)." -ForegroundColor Green
        }
        finally { Pop-Location }
    }
    else {
        Write-Host "  Sin cambios respecto a la plantilla." -ForegroundColor DarkGray
    }
}

Push-Location $destino
try {
    Write-Host ("  Rama: " + (& git rev-parse --abbrev-ref HEAD)) -ForegroundColor DarkGray
    Write-Host ("  Commits: " + (& git rev-list --count HEAD)) -ForegroundColor DarkGray
}
finally { Pop-Location }

Write-Host "  Listo." -ForegroundColor Green