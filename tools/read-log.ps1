# 查找并复制相机 App 的日志
# 依次尝试：所有盘符 -> 本地常见目录 -> 提示手动操作
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$copyDir = Join-Path $root 'A7R2App\logs'

function Show-Drives {
    Write-Host ''
    Write-Host '============== 当前已挂载的磁盘 ==============' -ForegroundColor Cyan
    try {
        foreach ($d in [System.IO.DriveInfo]::GetDrives()) {
            $label = ''
            try { $label = $d.VolumeLabel } catch { }
            $kind = '其他'
            switch ($d.DriveType) {
                'Fixed' { $kind = '本机磁盘' }
                'Removable' { $kind = '可移动设备' }
                'Network' { $kind = '网络' }
                'CDRom' { $kind = '光驱' }
            }
            $ready = if ($d.IsReady) { '就绪' } else { '未就绪' }
            Write-Host ('  {0,-4} {1,-8} {2,-10} {3}' -f $d.Name, $kind, $ready, $label)
        }
    } catch {
        Write-Host '  (无法枚举磁盘)'
    }
    Write-Host '=============================================' -ForegroundColor Cyan
}

$candidates = @()

# 1) 所有盘符里的 A7R2APP\LOG.TXT
foreach ($d in [System.IO.DriveInfo]::GetDrives()) {
    try {
        if (-not $d.IsReady) { continue }
        $p = Join-Path $d.RootDirectory.FullName 'A7R2APP\LOG.TXT'
        if (Test-Path -LiteralPath $p) {
            $candidates += Get-Item -LiteralPath $p
        }
    } catch { }
}

# 2) 本地常见目录（用户可能已经把 LOG.TXT 拷出来了）
$localDirs = @(
    (Join-Path $env:USERPROFILE 'Downloads'),
    (Join-Path $env:USERPROFILE 'Desktop'),
    (Join-Path $env:USERPROFILE 'Documents'),
    $copyDir,
    $root
)
foreach ($dir in $localDirs) {
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    Get-ChildItem -LiteralPath $dir -Filter 'LOG*.TXT' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne 'LOG-from-camera.txt' } |
        ForEach-Object { $candidates += $_ }
    Get-ChildItem -LiteralPath $dir -Filter 'LOG*.txt' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne 'LOG-from-camera.txt' } |
        ForEach-Object { $candidates += $_ }
}

$candidates = $candidates | Sort-Object -Property FullName -Unique

if ($candidates.Count -eq 0) {
    Write-Host '[x] 没有找到 A7R2APP\LOG.TXT' -ForegroundColor Red
    Show-Drives
    Write-Host ''
    Write-Host '请按下面任一方式让日志能被读到：' -ForegroundColor Yellow
    Write-Host '  1) 把 SD 卡从相机取出，插到电脑读卡器 —— 会多出一个盘符，直接再跑一次本脚本'
    Write-Host '  2) 相机用 USB 连电脑，在相机菜单里把 USB 连接方式改成「Mass Storage / 大容量存储」'
    Write-Host '     （Auto / MTP 模式下电脑只看到「设备」，没有盘符，本脚本读不到）'
    Write-Host '  3) 若你已经把 LOG.TXT 拷到 下载/桌面/文档，本脚本会自动找到它'
    Write-Host ''
    Write-Host '注意：App 必须在相机上至少启动过一次，才会生成这个文件：'
    Write-Host '  MENU -> 应用程序 -> 应用程序列表 -> 延时摄影'
    if (-not $env:NOPAUSE) { Read-Host '按回车退出' | Out-Null }
    exit 1
}

# 相机卡优先（可移动磁盘），其次才是本地拷贝；同组内取最新
$picked = $candidates |
    Sort-Object @{ Expression = { if ($_.FullName.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { 1 } else { 0 } } },
                @{ Expression = { $_.LastWriteTime }; Descending = $true } |
    Select-Object -First 1

if ($candidates.Count -gt 1) {
    Write-Host '找到多个日志文件：' -ForegroundColor Yellow
    foreach ($c in $candidates) {
        Write-Host ('  {0}  {1:yyyy-MM-dd HH:mm}  {2:N0} KB' -f $c.FullName, $c.LastWriteTime, ($c.Length / 1KB))
    }
    Write-Host ('使用最新的：{0}' -f $picked.FullName) -ForegroundColor Green
}

Write-Host ''
Write-Host ('[√] 日志：{0}' -f $picked.FullName) -ForegroundColor Green
Write-Host ('    大小 {0:N0} KB，修改时间 {1}' -f ($picked.Length / 1KB), $picked.LastWriteTime)

if (-not (Test-Path -LiteralPath $copyDir)) {
    New-Item -ItemType Directory -Force -Path $copyDir | Out-Null
}
$dest = Join-Path $copyDir 'LOG-from-camera.txt'
if ($picked.FullName -ne $dest) {
    Copy-Item -LiteralPath $picked.FullName -Destination $dest -Force
}
Write-Host ('[√] 已复制到：{0}' -f $dest) -ForegroundColor Green

# ---- 摘要 ----
$text = Get-Content -LiteralPath $dest -Encoding UTF8 -ErrorAction SilentlyContinue
$checks = [ordered]@{
    'App 启动'      = '=== app started ==='
    '相机已打开'    = 'CameraEx.open() ok'
    '实时取景'      = 'preview started'
    '拍过照片'      = 'takePicture()'
    '间隔拍摄'      = 'interval START'
    '有报错(E)'     = ' E '
}
Write-Host ''
Write-Host '============== 摘要 ==============' -ForegroundColor Cyan
foreach ($k in $checks.Keys) {
    $hit = $text | Select-String -SimpleMatch $checks[$k] -Quiet
    if ($hit) { Write-Host ('  [√] {0}' -f $k) -ForegroundColor Green }
    else { Write-Host ('  [ ] {0}' -f $k) -ForegroundColor DarkGray }
}

Write-Host ''
Write-Host '============== 最后 40 行 ==============' -ForegroundColor Cyan
$text | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }

Write-Host ''
Write-Host '完整日志已保存到上面那个路径，可直接把这个文件发给我。' -ForegroundColor Yellow
if (-not $env:NOPAUSE) { Read-Host '按回车退出' | Out-Null }
