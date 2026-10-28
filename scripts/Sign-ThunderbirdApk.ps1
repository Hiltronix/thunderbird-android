param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [Parameter(Mandatory = $true)]
    [string]$KeyStorePath,

    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,

    [string]$AndroidSdkPath = $(if ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT) {
        $env:ANDROID_SDK_ROOT
    } else {
        Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    })
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sourceApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$keyStoreFullPath = (Resolve-Path -LiteralPath $KeyStorePath).Path
$buildToolsRoot = Join-Path $AndroidSdkPath 'build-tools'

if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
    throw "Android SDK build-tools were not found: $buildToolsRoot"
}

$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Where-Object {
        (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat')) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'zipalign.exe')) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe'))
    } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

if ($null -eq $buildTools) {
    throw 'The Android SDK needs a build-tools installation containing aapt, zipalign and apksigner.'
}

$aaptPath = Join-Path $buildTools.FullName 'aapt.exe'
$zipAlignPath = Join-Path $buildTools.FullName 'zipalign.exe'
$apkSignerPath = Join-Path $buildTools.FullName 'apksigner.bat'

$packageInfo = & $aaptPath dump badging $sourceApkPath
if ($LASTEXITCODE -ne 0 -or -not ($packageInfo -match "package: name='net\.thunderbird\.android\.hiltronix'")) {
    throw 'APK package identification failed or the APK is not the Hiltronix Thunderbird build.'
}

$outputFolder = Split-Path -Path $sourceApkPath -Parent
$alignedApkPath = Join-Path $outputFolder 'thunderbird-10.1-hiltronix-aligned.apk'
$signedApkPath = Join-Path $outputFolder 'thunderbird-10.1-hiltronix-signed.apk'

try {
    & $zipAlignPath -f -p 4 $sourceApkPath $alignedApkPath
    if ($LASTEXITCODE -ne 0) {
        throw 'APK alignment failed.'
    }

    # Prompt for key passwords interactively rather than storing them in the repository.
    & $apkSignerPath sign --ks $keyStoreFullPath --ks-key-alias $KeyAlias --out $signedApkPath $alignedApkPath
    if ($LASTEXITCODE -ne 0) {
        throw 'APK signing failed.'
    }

    & $apkSignerPath verify --verbose --print-certs $signedApkPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Signed APK verification failed.'
    }

    Write-Host "Signed APK: $signedApkPath"
} finally {
    if (Test-Path -LiteralPath $alignedApkPath) {
        Remove-Item -LiteralPath $alignedApkPath -Force
    }
}
