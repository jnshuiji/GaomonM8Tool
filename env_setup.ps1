<#
.SYNOPSIS
    Gaomon M8 Toolchain - PowerShell Environment Initialization & Toolchain Setup
    Integrates Java, Android SDK/NDK, Rust, CMake, LLVM/Clang, Scoop and sccache with uv.

.USAGE
    . .\env_setup.ps1
#>

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 Initializing Gaomon M8 Engineering Development Suite  " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# 0. Smart JDK Detection & Runtime Isolation
# AGP 8.7.x / Gradle 8.13 has known incompatibilities when the daemon runs on Java 24+.
# Auto-bind JAVA_HOME to Android Studio JBR (OpenJDK 21 LTS) or installed JDK 17/21 if current Java is 24+.
$currentJavaVer = (java -version 2>&1 | Select-String "version") -replace '.*version "([^".]+).*', '$1'
if ([int]$currentJavaVer -ge 24 -or -not $env:JAVA_HOME) {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "$env:ProgramFiles\Java\jdk-21",
        "$env:ProgramFiles\Java\jdk-17",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-17*",
        "$env:USERPROFILE\scoop\apps\openjdk21\current",
        "$env:USERPROFILE\scoop\apps\openjdk17\current"
    )
    foreach ($cand in $candidates) {
        $resolved = Resolve-Path $cand -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path "$($resolved.Path)\bin\javac.exe")) {
            $env:JAVA_HOME = $resolved.Path
            $env:Path = "$($resolved.Path)\bin;$env:Path"
            Write-Host "✅ Auto-bound JAVA_HOME to stable runtime: $env:JAVA_HOME" -ForegroundColor Green
            break
        }
    }
}

# 1. Android SDK & NDK Environment
if (-not $env:ANDROID_HOME) {
    $sdkCandidates = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:ProgramFiles\Android\Sdk"
    )
    foreach ($c in $sdkCandidates) {
        if (Test-Path $c) {
            $env:ANDROID_HOME = $c
            break
        }
    }
}
if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\ndk")) {
    $latestNdk = Get-ChildItem "$env:ANDROID_HOME\ndk" | Sort-Object Name -Descending | Select-Object -First 1
    if ($latestNdk) {
        $env:ANDROID_NDK = $latestNdk.FullName
        $env:ANDROID_NDK_ROOT = $env:ANDROID_NDK
    }
}

# 2. Compiler & Build Cache Acceleration (sccache & Ninja)
$env:RUSTC_WRAPPER = "sccache"
$env:CMAKE_C_COMPILER_LAUNCHER = "sccache"
$env:CMAKE_CXX_COMPILER_LAUNCHER = "sccache"
$env:CMAKE_GENERATOR = "Ninja"

# 3. Scoop LLVM / Clang Toolchain Path Assurance
$LLVM_BIN = "$env:USERPROFILE\scoop\apps\llvm\current\bin"
if (Test-Path $LLVM_BIN) {
    if ($env:Path -notlike "*$LLVM_BIN*") {
        $env:Path = "$LLVM_BIN;$env:Path"
    }
}

# 4. Developer Aliases & Shortcut Functions
function dev-status { uv run python tools/dev.py status }
function dev-connect { uv run python tools/dev.py connect $args }
function dev-inputs { uv run python tools/dev.py inspect-inputs $args }
function dev-deploy { uv run python tools/dev.py build-deploy $args }
function dev-screen { uv run python tools/dev.py screen $args }
function dev-logcat { uv run python tools/dev.py logcat $args }
function dev-sccache { sccache --show-stats }

Write-Host "✅ JDK Runtime: $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'System Default' })" -ForegroundColor Green
Write-Host "✅ Android SDK: $env:ANDROID_HOME" -ForegroundColor Green
Write-Host "✅ Android NDK: $env:ANDROID_NDK" -ForegroundColor Green
Write-Host "✅ sccache enabled for Rust (RUSTC_WRAPPER) and CMake" -ForegroundColor Green
Write-Host "✅ CMake generator set to Ninja" -ForegroundColor Green
Write-Host "✅ Shortcuts registered: " -ForegroundColor Magenta
Write-Host "   - dev-status   : Inspect full environment status"
Write-Host "   - dev-connect  : Connect & check tablet state"
Write-Host "   - dev-inputs   : Scan /proc/bus/input/devices"
Write-Host "   - dev-deploy   : Gradle build & wireless ADB deploy"
Write-Host "   - dev-screen   : Wireless scrcpy screen mirroring"
Write-Host "   - dev-logcat   : Filter Gaomon driver & hook logs"
Write-Host "   - dev-sccache  : View build cache statistics"
Write-Host "==========================================================" -ForegroundColor Cyan
