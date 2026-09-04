param([switch]$Update)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskCache = Join-Path $taskRoot 'runtime/build/native-runtime-downloads'
New-Item -ItemType Directory -Force $taskCache | Out-Null
$taskRecords = @()
$taskPinned = Get-Content -LiteralPath (Join-Path $taskRoot 'runtime/src/main/jniLibs/manifest.json') -Raw | ConvertFrom-Json
foreach ($taskArch in @(@('x86_64','x86_64'), @('aarch64','arm64-v8a'), @('arm','armeabi-v7a'))) {
    $taskResponse = Invoke-WebRequest -TimeoutSec 60 -Uri "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-$($taskArch[0])/Packages"
    $taskIndex = if ($taskResponse.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($taskResponse.Content) } else { $taskResponse.Content }
    $taskNative = Join-Path $taskRoot "runtime/src/main/jniLibs/$($taskArch[1])"
    New-Item -ItemType Directory -Force $taskNative | Out-Null
    foreach ($taskName in @('proot','libtalloc','libandroid-shmem')) {
        $taskEntry = ($taskIndex -split '\r?\n\r?\n') | Where-Object { $_ -match "(?m)^Package: $taskName\r?$" } | Select-Object -First 1
        if (!$taskEntry) { throw "Missing package: $taskName" }
        $taskRelative = [regex]::Match($taskEntry,'(?m)^Filename: (.+)$').Groups[1].Value.Trim()
        $taskSha = [regex]::Match($taskEntry,'(?m)^SHA256: (.+)$').Groups[1].Value.Trim()
        $taskVersion = [regex]::Match($taskEntry,'(?m)^Version: (.+)$').Groups[1].Value.Trim()
        $taskUrl = "https://packages.termux.dev/apt/termux-main/$taskRelative"
        if (!$Update) {
            $taskPin = $taskPinned | Where-Object { $_.abi -eq $taskArch[1] -and $_.package -eq $taskName } | Select-Object -First 1
            if (!$taskPin) { throw "Missing pinned package: $taskName" }
            $taskUrl = $taskPin.url
            $taskRelative = $taskPin.url
            $taskSha = $taskPin.packageSha256
            $taskVersion = $taskPin.version
        }
        $taskDeb = Join-Path $taskCache ([IO.Path]::GetFileName($taskRelative))
        Invoke-WebRequest -TimeoutSec 60 -Uri $taskUrl -OutFile $taskDeb
        if ((Get-FileHash -LiteralPath $taskDeb -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskSha) { throw 'Package checksum mismatch' }
        $taskExtract = Join-Path $taskCache ([IO.Path]::GetFileNameWithoutExtension($taskDeb))
        New-Item -ItemType Directory -Force $taskExtract | Out-Null
        & tar -xmf $taskDeb -C $taskExtract 'data.tar.xz'
        if ($LASTEXITCODE -ne 0) { throw 'Could not extract deb data member' }
        $taskData = Join-Path $taskExtract 'data.tar.xz'
        $taskNames = & tar -tf $taskData
        $taskFiles = switch ($taskName) {
            'proot' {
                [pscustomobject]@{ entry='./data/data/com.termux/files/usr/bin/proot'; file='libskullshell_proot.so' }
                [pscustomobject]@{ entry='./data/data/com.termux/files/usr/libexec/proot/loader'; file='libskullshell_loader.so' }
            }
            'libtalloc' { [pscustomobject]@{ entry=($taskNames | Where-Object { $_ -match '/libtalloc\.so\.\d+\.\d+\.\d+$' } | Select-Object -First 1); file='libtalloc.so' } }
            'libandroid-shmem' { [pscustomobject]@{ entry='./data/data/com.termux/files/usr/lib/libandroid-shmem.so'; file='libandroid-shmem.so' } }
        }
        foreach ($taskFile in $taskFiles) {
            & tar -xmf $taskData -C $taskExtract $taskFile.entry
            if ($LASTEXITCODE -ne 0) { throw "Could not extract $($taskFile.entry)" }
            $taskDest = Join-Path $taskNative $taskFile.file
            Copy-Item -LiteralPath (Join-Path $taskExtract $taskFile.entry) -Destination $taskDest
            $taskRecords += [ordered]@{ abi=$taskArch[1]; package=$taskName; version=$taskVersion; url=$taskUrl; packageSha256=$taskSha; file=$taskFile.file; sha256=(Get-FileHash -LiteralPath $taskDest -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
    }
}
$taskRecords | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'runtime/src/main/jniLibs/manifest.json')
