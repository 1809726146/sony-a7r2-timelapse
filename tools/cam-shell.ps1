# 在相机上执行一批 shell 命令（需要管理员权限）
# 用法：
#   powershell -File tools\cam-shell.ps1 -CommandFile <本机输入文件> -OutFile <本机输出文件>
param(
    [Parameter(Mandatory = $true)][string]$CommandFile,
    [Parameter(Mandatory = $true)][string]$OutFile
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$py = Join-Path $root 'toolchain\python\python.exe'
$repo = Join-Path $root 'pmca-re'

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Host '[x] 需要管理员权限'; exit 2 }
if (-not (Test-Path $py)) { Write-Host "[x] python missing: $py"; exit 1 }

$errFile = [System.IO.Path]::ChangeExtension($OutFile, '.err.txt')
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONWARNINGS = 'ignore'
$env:PATH = (Split-Path $py) + ';' + $env:PATH

$p = Start-Process -FilePath $py `
    -ArgumentList 'pmca-console.py', 'serviceshell' `
    -WorkingDirectory $repo `
    -RedirectStandardInput $CommandFile `
    -RedirectStandardOutput $OutFile `
    -RedirectStandardError $errFile `
    -NoNewWindow -PassThru -Wait
Write-Host "exit=$($p.ExitCode)"
if (Test-Path $errFile) { Write-Host '--- stderr ---'; Get-Content $errFile -Tail 8 }
