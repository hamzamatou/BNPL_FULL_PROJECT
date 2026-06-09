# Recree .venv si l'ancien Python (ex. 3.14 supprime) est introuvable.
# Usage (PowerShell) :
#   cd bnpl-data-pipeline
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   .\setup_venv.ps1

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

$candidates = @(
    "$env:LOCALAPPDATA\Programs\Python\Python313\python.exe",
    "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe",
    "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe"
)

$Python = $null
foreach ($p in $candidates) {
    if (Test-Path $p) {
        $Python = $p
        break
    }
}

if (-not $Python) {
    try {
        $pyVer = & py -3.13 -c "import sys; print(sys.executable)" 2>$null
        if ($pyVer -and (Test-Path $pyVer)) { $Python = $pyVer.Trim() }
    } catch { }
}

if (-not $Python) {
    Write-Host "Aucun Python 3.11+ trouve. Installez Python 3.13 depuis https://www.python.org/downloads/" -ForegroundColor Red
    Write-Host "Ou : winget install Python.Python.3.13"
    exit 1
}

Write-Host "Python utilise : $Python"
& $Python --version

$venv = Join-Path $Root ".venv"
if (Test-Path $venv) {
    Write-Host "Suppression de l'ancien .venv (Python casse)..."
    Remove-Item -Recurse -Force $venv
}

Write-Host "Creation du venv..."
& $Python -m venv $venv

$pip = Join-Path $venv "Scripts\pip.exe"
$pyVenv = Join-Path $venv "Scripts\python.exe"

& $pyVenv -m pip install --upgrade pip
& $pip install -r (Join-Path $Root "requirements.txt")

Write-Host ""
Write-Host "OK. Activez puis lancez l'entrainement :" -ForegroundColor Green
Write-Host "  .\.venv\Scripts\Activate.ps1"
Write-Host "  python train_GBMlight.py"
