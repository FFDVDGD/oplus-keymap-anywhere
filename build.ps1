param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Run([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Tool exited with $LASTEXITCODE"
    }
}

if (!$Jdk) {
    $Jdk = "$env:ProgramFiles\Android\Android Studio\jbr"
}
$tools = Get-ChildItem "$Sdk\build-tools" -Directory |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$tools = $tools.FullName
$java = Join-Path $Jdk 'bin\java.exe'
$scratch = Join-Path $PSScriptRoot '.amp\in'
$output = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force $scratch, $output | Out-Null

# Local development signing key, kept out of the source and the distributable APK.
$key = Join-Path $scratch 'development.jks'
if (!(Test-Path $key)) {
    Run "$Jdk\bin\keytool.exe" @('-genkeypair', '-keystore', $key,
        '-storepass', 'android', '-keypass', 'android', '-alias', 'gamehub',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
        '-dname', 'CN=GameHub Keyboard Local Development')
}
$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $Sdk
Run "$PSScriptRoot\gradlew.bat" @('-p', $PSScriptRoot, '--no-daemon', '--console=plain', 'assembleRelease')
$signed = Get-ChildItem "$scratch\gradle-build\outputs\apk\release" -Filter '*.apk' |
    Select-Object -ExpandProperty FullName
Run $java @('-jar', "$tools\lib\apksigner.jar", 'verify', '--verbose', $signed)
$apk = Join-Path $output 'oplus-keymap-anywhere.apk'
Copy-Item -LiteralPath $signed -Destination $apk -Force
Run $java @('-jar', "$tools\lib\apksigner.jar", 'verify', '--verbose', $apk)
Write-Host "Built $apk"
