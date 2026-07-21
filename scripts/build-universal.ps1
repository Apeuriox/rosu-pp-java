param(
    [Parameter(Mandatory = $false)]
    [string]$LinuxLibrary,

    [Parameter(Mandatory = $false)]
    [string]$Java21Home = $env:JAVA21_HOME,

    [Parameter(Mandatory = $false)]
    [string]$Java22Home = $env:JAVA22_HOME
)

$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$rustToolchain = '1.97.0'
$dockerImage = 'rust:1.97.0-bookworm'
$stableJarName = 'rosu-pp-java-0.0.1.jar'
$previewJarName = 'rosu-pp-java-0.0.1-java21-preview.jar'
$originalJavaHome = $env:JAVA_HOME

function Assert-LastExitCode([string]$operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$operation failed with exit code $LASTEXITCODE"
    }
}

function Resolve-JavaHome(
    [string]$configuredHome,
    [string]$environmentName,
    [int]$minimumVersion,
    [bool]$exactVersion
) {
    $candidates = @($configuredHome, $env:JAVA_HOME) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($candidate in $candidates) {
        $java = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
            continue
        }
        $version = (& $java -version 2>&1 | Select-Object -First 1).ToString()
        if ($version -notmatch 'version "(?<major>[0-9]+)(?:\.|\")') {
            continue
        }
        $major = [int]$Matches.major
        if (($exactVersion -and $major -eq $minimumVersion) -or
            (-not $exactVersion -and $major -ge $minimumVersion)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $requirement = if ($exactVersion) { "JDK $minimumVersion" } else { "JDK $minimumVersion or newer" }
    throw "$requirement is required. Set $environmentName or pass the corresponding script parameter."
}

function Stage-NativeResources([string]$windowsDll, [string]$linuxSo) {
    $windowsResource = Join-Path $repository 'target\native-resources\META-INF\native\windows-x86_64'
    $linuxResource = Join-Path $repository 'target\native-resources\META-INF\native\linux-x86_64'
    New-Item -ItemType Directory -Force $windowsResource, $linuxResource | Out-Null
    Copy-Item -LiteralPath $windowsDll -Destination (Join-Path $windowsResource 'rosu_pp_ffi.dll') -Force
    Copy-Item -LiteralPath $linuxSo -Destination (Join-Path $linuxResource 'librosu_pp_ffi.so') -Force
}

function Build-JavaJar(
    [string]$javaHome,
    [string]$profile,
    [string]$jarName,
    [string]$label,
    [string]$windowsDll,
    [string]$linuxSo
) {
    $env:JAVA_HOME = $javaHome
    & .\mvnw.cmd -B --no-transfer-progress clean | Out-Host
    Assert-LastExitCode "$label Maven clean"
    Stage-NativeResources $windowsDll $linuxSo

    $arguments = @('-B', '--no-transfer-progress', 'package')
    if (-not [string]::IsNullOrWhiteSpace($profile)) {
        $arguments += "-P$profile"
    }
    & .\mvnw.cmd @arguments | Out-Host
    Assert-LastExitCode "$label Maven package"

    $jar = Join-Path $repository "target\$jarName"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "$label JAR was not produced at $jar"
    }
    return $jar
}

function Assert-Jar(
    [string]$jar,
    [string]$javap,
    [string]$expectedMajor,
    [string]$expectedMinor
) {
    $jarTool = Join-Path (Split-Path (Split-Path $javap)) 'bin\jar.exe'
    $entries = & $jarTool --list --file $jar
    Assert-LastExitCode "JAR content listing for $jar"
    foreach ($required in @(
        'META-INF/native/windows-x86_64/rosu_pp_ffi.dll',
        'META-INF/native/linux-x86_64/librosu_pp_ffi.so'
    )) {
        if ($entries -notcontains $required) {
            throw "$jar is missing $required"
        }
    }

    $classInfo = & $javap -verbose -classpath $jar 'me.aloic.rosupp.internal.NativeBridge'
    Assert-LastExitCode "Class version inspection for $jar"
    if (-not ($classInfo -match "major version: $expectedMajor") -or
        -not ($classInfo -match "minor version: $expectedMinor")) {
        throw "$jar has an unexpected class-file version"
    }
}

Push-Location $repository
try {
    $resolvedJava21 = Resolve-JavaHome $Java21Home 'JAVA21_HOME' 21 $true
    $resolvedJava22 = Resolve-JavaHome $Java22Home 'JAVA22_HOME' 22 $false

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

    $jarCache = Join-Path $repository 'native\target\universal-jars'
    New-Item -ItemType Directory -Force $jarCache | Out-Null
    $cachedPreviewJar = Join-Path $jarCache $previewJarName
    Remove-Item -LiteralPath $cachedPreviewJar -Force -ErrorAction SilentlyContinue

    $previewJar = Build-JavaJar $resolvedJava21 'java21-preview' $previewJarName `
        'Java 21 preview' $windowsDll $linuxSo
    Copy-Item -LiteralPath $previewJar -Destination $cachedPreviewJar -Force

    $stableJar = Build-JavaJar $resolvedJava22 '' $stableJarName `
        'Java 22+ stable FFM' $windowsDll $linuxSo
    $previewJar = Join-Path $repository "target\$previewJarName"
    Copy-Item -LiteralPath $cachedPreviewJar -Destination $previewJar -Force

    $javap = Join-Path $resolvedJava22 'bin\javap.exe'
    Assert-Jar $previewJar $javap '65' '65535'
    Assert-Jar $stableJar $javap '66' '0'

    Write-Host "Java 21 preview universal JAR: $previewJar"
    Write-Host "Java 22+ stable universal JAR: $stableJar"
} finally {
    if ([string]::IsNullOrWhiteSpace($originalJavaHome)) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    Pop-Location
}
