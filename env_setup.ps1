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

# 1. Android SDK & NDK Environment
$env:ANDROID_HOME = "C:\Users\Pochita\AppData\Local\Android\Sdk"
$env:ANDROID_NDK = "$env:ANDROID_HOME\ndk\29.0.13599879"
$env:ANDROID_NDK_ROOT = $env:ANDROID_NDK

# 2. Compiler & Build Cache Acceleration (sccache & Ninja)
$env:RUSTC_WRAPPER = "sccache"
$env:CMAKE_C_COMPILER_LAUNCHER = "sccache"
$env:CMAKE_CXX_COMPILER_LAUNCHER = "sccache"
$env:CMAKE_GENERATOR = "Ninja"

# 3. Scoop LLVM / Clang Toolchain Path Assurance
$LLVM_BIN = "C:\Users\Pochita\scoop\apps\llvm\current\bin"
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

Write-Host "✅ Android SDK / NDK 29 Bound ($env:ANDROID_NDK)" -ForegroundColor Green
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
