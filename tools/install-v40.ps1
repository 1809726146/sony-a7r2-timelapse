# 延时摄影 v4.0 —— 提权安装到相机（由 DSH 或用户手动运行）
# 用法（必须是管理员）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\install-v40.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$py   = Join-Path $root 'toolchain\python\python.exe'
$repo = Join-Path $root 'pmca-re'
$apk  = Join-Path $root 'A7R2App\build\A7R2App-debug.apk'
$log  = Join-Path $root 'A7R2App\build\install.log'

foreach ($p in @($py, $repo, $apk)) {
    if (-not (Test-Path $p)) { Write-Host "[x] missing: $p"; exit 1 }
}

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Host "[x] 需要管理员权限"; exit 2 }

Write-Host "APK : $apk"
Write-Host "LOG : $log"
Write-Host "---- pmca install ----"

$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONWARNINGS = 'ignore'
$env:PATH = (Split-Path $py) + ';' + $env:PATH

Push-Location $repo
try {
    $out = & $py 'pmca-console.py' 'install' '-d' 'native' '-f' $apk 2>&1 | Out-String
} finally {
    Pop-Location
}
Set-Content -Path $log -Value $out -Encoding UTF8
Write-Host $out

if ($out -match 'Task completed successfully') {
    Write-Host "`r`nRESULT: INSTALL OK"
} elseif ($out -match 'No devices found') {
    Write-Host "`r`nRESULT: NO CAMERA FOUND"
} elseif ($out -match 'Access denied|insufficient permissions|GenericUsbException') {
    Write-Host "`r`nRESULT: NEED ADMIN / DRIVER BUSY"
} else {
    Write-Host "`r`nRESULT: UNKNOWN — see log"
}
