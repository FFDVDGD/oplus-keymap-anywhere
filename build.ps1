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
$platform = Get-ChildItem "$Sdk\platforms" -Directory |
    Where-Object Name -Match '^android-\d+$' |
    Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending |
    Select-Object -First 1
$tools = Get-ChildItem "$Sdk\build-tools" -Directory |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$androidJar = Join-Path $platform.FullName 'android.jar'
$tools = $tools.FullName
$java = Join-Path $Jdk 'bin\java.exe'
$scratch = Join-Path $PSScriptRoot '.amp\in'
$build = Join-Path $scratch 'build'
$deps = Join-Path $scratch 'deps'
$output = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force $build, "$build\classes", $deps, $output | Out-Null

$api = Join-Path $deps 'libxposed-api-102.0.0.jar'
if (!(Test-Path $api)) {
    $aar = Join-Path $deps 'libxposed-api-102.0.0.aar'
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar' -OutFile $aar
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($aar)
    try {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
            $archive.GetEntry('classes.jar'), $api, $true)
    } finally {
        $archive.Dispose()
    }
}
$sources = @(Get-ChildItem "$PSScriptRoot\src\main\java" -Recurse -Filter '*.java' |
    ForEach-Object FullName)
Run "$Jdk\bin\javac.exe" (@('--release', '8', '-encoding', 'UTF-8',
    '-classpath', "$androidJar;$api", '-d', "$build\classes") + $sources)
Run "$Jdk\bin\jar.exe" @('cf', "$build\classes.jar", '-C', "$build\classes", '.')
Run $java @('-cp', "$tools\lib\d8.jar", 'com.android.tools.r8.D8',
    '--min-api', '30', '--lib', $androidJar, '--classpath', $api,
    '--output', $build, "$build\classes.jar")
Run "$tools\aapt2.exe" @('compile', '--dir', "$PSScriptRoot\src\main\res",
    '-o', "$build\resources.zip")
Run "$tools\aapt2.exe" @('link', '-I', $androidJar,
    '--manifest', "$PSScriptRoot\src\main\AndroidManifest.xml",
    '-o', "$build\unsigned.apk", "$build\resources.zip")
Run "$Jdk\bin\jar.exe" @('uf', "$build\unsigned.apk", '-C', $build, 'classes.dex',
    '-C', "$PSScriptRoot\src\main\resources", 'META-INF')
Run "$tools\zipalign.exe" @('-f', '4', "$build\unsigned.apk", "$build\aligned.apk")

# Local development signing key, kept out of the source and the distributable APK.
$key = Join-Path $scratch 'development.jks'
if (!(Test-Path $key)) {
    Run "$Jdk\bin\keytool.exe" @('-genkeypair', '-keystore', $key,
        '-storepass', 'android', '-keypass', 'android', '-alias', 'gamehub',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
        '-dname', 'CN=GameHub Keyboard Local Development')
}
$apk = Join-Path $output 'oplus-keymap-anywhere.apk'
Run $java @('-jar', "$tools\lib\apksigner.jar", 'sign', '--ks', $key,
    '--ks-key-alias', 'gamehub', '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android', '--out', $apk, "$build\aligned.apk")
Run $java @('-jar', "$tools\lib\apksigner.jar", 'verify', '--verbose', $apk)
Write-Host "Built $apk"
