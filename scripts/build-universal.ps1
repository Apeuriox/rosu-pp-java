param(
    [Parameter(Mandatory = $false)]
    [string]$LinuxLibrary
)

$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$rustToolchain = '1.97.0'
$dockerImage = 'rust:1.97.0-bookworm'

function Assert-LastExitCode([string]$operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$operation failed with exit code $LASTEXITCODE"
    }
}

function Assert-Java21 {
    $java = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        (Get-Command java -ErrorAction Stop).Source
    }
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw "JAVA_HOME does not point to a Java installation: $env:JAVA_HOME"
    }
    $version = (& $java -version 2>&1 | Select-Object -First 1).ToString()
    if ($version -notmatch 'version "21(?:\.|\")') {
        throw "Java 21 is required, but Maven would use: $version. Set JAVA_HOME to a JDK 21 installation."
    }
}

Push-Location $repository
try {
    Assert-Java21

    & cargo "+$rustToolchain" build --manifest-path native/Cargo.toml --release --locked -p rosu-pp-ffi
    Assert-LastExitCode 'Windows native build'
    $windowsDll = Join-Path $repository 'native\target\release\rosu_pp_ffi.dll'
    if (-not (Test-Path -LiteralPath $windowsDll -PathType Leaf)) {
        throw "Windows DLL was not produced at $windowsDll"
    }

    if ($LinuxLibrary) {
        $resolvedLinux = (Resolve-Path -LiteralPath $LinuxLibrary).Path
        $linuxCache = Join-Path $repository 'native\target\universal-input\librosu_pp_ffi.so'
        New-Item -ItemType Directory -Force (Split-Path $linuxCache) | Out-Null
        Copy-Item -LiteralPath $resolvedLinux -Destination $linuxCache -Force
        $linuxSo = $linuxCache
    } else {
        & docker version | Out-Null
        Assert-LastExitCode 'Docker availability check'
        $mount = "type=bind,source=$repository,target=/work"
        & docker run --rm `
            --mount $mount `
            --mount 'type=volume,source=rosu-pp-java-cargo-registry,target=/usr/local/cargo/registry' `
            --mount 'type=volume,source=rosu-pp-java-cargo-git,target=/usr/local/cargo/git' `
            --workdir /work `
            --env CARGO_TARGET_DIR=/work/native/target/docker-linux `
            $dockerImage `
            cargo build --manifest-path native/Cargo.toml --release --locked -p rosu-pp-ffi
        Assert-LastExitCode 'Linux native build in Docker'
        $linuxSo = Join-Path $repository 'native\target\docker-linux\release\librosu_pp_ffi.so'
    }
    if (-not (Test-Path -LiteralPath $linuxSo -PathType Leaf)) {
        throw "Linux shared library was not produced at $linuxSo"
    }

    & .\mvnw.cmd -B --no-transfer-progress clean
    Assert-LastExitCode 'Maven clean'

    $windowsResource = Join-Path $repository 'target\native-resources\META-INF\native\windows-x86_64'
    $linuxResource = Join-Path $repository 'target\native-resources\META-INF\native\linux-x86_64'
    New-Item -ItemType Directory -Force $windowsResource, $linuxResource | Out-Null
    Copy-Item -LiteralPath $windowsDll -Destination (Join-Path $windowsResource 'rosu_pp_ffi.dll') -Force
    Copy-Item -LiteralPath $linuxSo -Destination (Join-Path $linuxResource 'librosu_pp_ffi.so') -Force

    & .\mvnw.cmd -B --no-transfer-progress package
    Assert-LastExitCode 'Universal JAR build'

    $jar = Join-Path $repository 'target\rosu-pp-java-0.0.1.jar'
    $jarTool = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\jar.exe'
    } else {
        (Get-Command jar -ErrorAction Stop).Source
    }
    $entries = & $jarTool --list --file $jar
    Assert-LastExitCode 'JAR content listing'
    foreach ($required in @(
        'META-INF/native/windows-x86_64/rosu_pp_ffi.dll',
        'META-INF/native/linux-x86_64/librosu_pp_ffi.so'
    )) {
        if ($entries -notcontains $required) {
            throw "Universal JAR is missing $required"
        }
    }

    Write-Host "Universal JAR created: $jar"
} finally {
    Pop-Location
}
