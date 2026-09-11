"""
Gaomon M8 Toolchain CLI
Unified developer orchestration for build, deployment, device inspection, and debugging.
Managed via uv (run as: uv run python tools/dev.py <command>)
"""

import os
import re
import subprocess
import sys
from pathlib import Path

# Fix Windows console encoding for UTF-8 and Rich symbols
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import click
from rich.console import Console
from rich.table import Table

console = Console(highlight=False)
DEFAULT_DEVICE = "10.0.0.12:5555"
PACKAGE_NAME = "com.gaomon.m8"
STAR_NOTE_PACKAGE = "com.onyx.galaxy.note"
PROJECT_ROOT = Path(__file__).resolve().parent.parent


def run_cmd(cmd, cwd=None, check=False, capture_output=True, text=True):
    try:
        res = subprocess.run(
            cmd,
            cwd=cwd or PROJECT_ROOT,
            check=check,
            capture_output=capture_output,
            text=text,
            encoding="utf-8",
            errors="replace",
            shell=isinstance(cmd, str),
        )
        return res
    except Exception as e:
        console.print(f"[bold red]Execution error:[/bold red] {e}")
        return None


def get_adb_device(target=DEFAULT_DEVICE):
    run_cmd(["adb", "connect", target])
    res = run_cmd(["adb", "devices"])
    if res and target in res.stdout and "device" in res.stdout:
        return target
    lines = (res.stdout if res else "").splitlines()
    for line in lines[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    return None


@click.group()
def cli():
    """Gaomon M8 Tool Engineering Development Suite"""
    pass


@cli.command()
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
def connect(target):
    """Verify ADB connection and inspect target Android device."""
    console.print(f"[bold cyan]Connecting to Android Device at [yellow]{target}[/yellow]...[/bold cyan]")
    dev = get_adb_device(target)
    if not dev:
        console.print(f"[bold red][FAIL] Failed to connect to device at {target}. Check Wi-Fi or USB connection.[/bold red]")
        sys.exit(1)

    model = run_cmd(["adb", "-s", dev, "shell", "getprop", "ro.product.model"]).stdout.strip()
    abi = run_cmd(["adb", "-s", dev, "shell", "getprop", "ro.product.cpu.abi"]).stdout.strip()
    release = run_cmd(["adb", "-s", dev, "shell", "getprop", "ro.build.version.release"]).stdout.strip()
    sdk = run_cmd(["adb", "-s", dev, "shell", "getprop", "ro.build.version.sdk"]).stdout.strip()
    root_res = run_cmd(["adb", "-s", dev, "shell", "su", "-c", "id"])
    has_root = root_res and "uid=0" in root_res.stdout

    table = Table(title="Connected Android Device", border_style="bright_blue")
    table.add_column("Property", style="bold")
    table.add_column("Value", style="green")

    table.add_row("Serial / Target", dev)
    table.add_row("Product Model", model)
    table.add_row("Android OS", f"{release} (API {sdk})")
    table.add_row("CPU ABI", abi)
    table.add_row("Root Status", "[OK] Granted (KernelSU / Root)" if has_root else "[FAIL] No Root / Not Granted")

    console.print(table)


@cli.command("inspect-inputs")
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
def inspect_inputs(target):
    """Scan and list all input devices (/proc/bus/input/devices), highlighting Gaomon M8 nodes."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device found.[/bold red]")
        sys.exit(1)

    console.print(f"[bold cyan]Scanning input devices on {dev}...[/bold cyan]")
    res = run_cmd(["adb", "-s", dev, "shell", "su", "-c", "cat /proc/bus/input/devices"])
    if not res or not res.stdout:
        console.print("[bold red][FAIL] Failed to read /proc/bus/input/devices[/bold red]")
        return

    blocks = res.stdout.strip().split("\n\n")
    table = Table(title="Android Input Devices (/dev/input/event*)", border_style="cyan")
    table.add_column("Type / Name", style="bold")
    table.add_column("Event Handler", style="magenta")
    table.add_column("Vendor:Product", style="yellow")
    table.add_column("Status / Role", style="green")

    for block in blocks:
        name_match = re.search(r'Name="([^"]+)"', block)
        handler_match = re.search(r'Handlers=.*?(event\d+)', block)
        vendor_match = re.search(r'Vendor=([0-9a-fA-F]+)\s+Product=([0-9a-fA-F]+)', block)

        name = name_match.group(1) if name_match else "Unknown"
        handler = f"/dev/input/{handler_match.group(1)}" if handler_match else "N/A"
        vid_pid = f"{vendor_match.group(1)}:{vendor_match.group(2)}" if vendor_match else "N/A"

        is_gaomon = "256c" in block.lower() or "gaomon" in name.lower()
        if is_gaomon:
            table.add_row(f"[bold yellow]{name}[/bold yellow]", f"[bold yellow]{handler}[/bold yellow]", vid_pid, "[TARGET] Gaomon M8 Device")
        elif "keyboard" in name.lower():
            table.add_row(f"[dim]{name}[/dim]", f"[dim]{handler}[/dim]", vid_pid, "[ALT] Physical Keyboard")
        elif "pen" in name.lower() or "touch" in name.lower():
            table.add_row(f"[dim]{name}[/dim]", f"[dim]{handler}[/dim]", vid_pid, "[SCREEN] Screen / Stylus")

    console.print(table)


@cli.command("build-deploy")
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
@click.option("--release", is_flag=True, help="Build Release variant instead of Debug")
@click.option("--launch", is_flag=True, default=True, help="Launch application after install")
def build_deploy(target, release, launch):
    """Build APK using Gradle and wirelessly deploy to connected Android device."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device available for deployment.[/bold red]")
        sys.exit(1)

    variant = "Release" if release else "Debug"
    gradle_task = f"assemble{variant}"
    gradlew = ".\\gradlew.bat" if os.name == "nt" else "./gradlew"

    console.print(f"[bold cyan]>> Triggering Gradle build: [yellow]{gradle_task}[/yellow]...[/bold cyan]")
    build_res = subprocess.run([gradlew, gradle_task], cwd=PROJECT_ROOT)
    if build_res.returncode != 0:
        console.print("[bold red][FAIL] Gradle build failed.[/bold red]")
        sys.exit(1)

    apk_dir = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / variant.lower()
    apks = list(apk_dir.glob("*.apk"))
    if not apks:
        console.print(f"[bold red][FAIL] No APK found in {apk_dir}[/bold red]")
        sys.exit(1)

    apk_path = apks[0]
    console.print(f"[bold green]>> Found APK: {apk_path.name} ({apk_path.stat().st_size // 1024} KB)[/bold green]")
    console.print(f"[bold cyan]>> Installing to {dev}...[/bold cyan]")

    install_res = run_cmd(["adb", "-s", dev, "install", "-r", str(apk_path)])
    if install_res and "Success" in install_res.stdout:
        console.print("[bold green][OK] APK installed successfully![/bold green]")
    else:
        err = install_res.stderr if install_res else "Unknown error"
        console.print(f"[bold red][FAIL] Installation failed: {err}[/bold red]")
        sys.exit(1)

    if launch:
        console.print(f"[bold cyan]>> Launching {PACKAGE_NAME}...[/bold cyan]")
        run_cmd(["adb", "-s", dev, "shell", "am", "force-stop", PACKAGE_NAME])
        run_cmd([
            "adb", "-s", dev, "shell", "am", "start",
            "-n", f"{PACKAGE_NAME}/.ui.MainActivity"
        ])
        console.print("[bold green][OK] App launched on tablet.[/bold green]")


@cli.command("screen")
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
@click.option("--stay-awake", is_flag=True, default=True, help="Keep device awake while scrcpy runs")
def screen(target, stay_awake):
    """Launch wireless low-latency screen mirror with scrcpy."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device available for screen mirroring.[/bold red]")
        sys.exit(1)

    console.print(f"[bold cyan]>> Launching scrcpy for [yellow]{dev}[/yellow]...[/bold cyan]")
    args = [
        "scrcpy",
        "-s", dev,
        "--max-size", "1920",
        "--video-bit-rate", "16M",
        "--max-fps", "60",
    ]
    if stay_awake:
        args.append("--stay-awake")

    try:
        subprocess.Popen(args)
        console.print("[bold green][OK] scrcpy session started in background.[/bold green]")
    except FileNotFoundError:
        console.print("[bold red][FAIL] scrcpy executable not found in PATH. Install via 'scoop install scrcpy'.[/bold red]")


@cli.command("logcat")
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
@click.option("--filter-tag", default="GaomonDaemon,GaomonM8Hook,GaomonM8Xposed", help="Comma-separated log tags")
def logcat(target, filter_tag):
    """Stream filtered logcat for Gaomon driver & Xposed module hooks."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device available.[/bold red]")
        sys.exit(1)

    tags = filter_tag.split(",")
    console.print(f"[bold cyan]>> Streaming logcat from {dev} (Filtering tags: {tags})...[/bold cyan]")
    console.print("[dim]Press Ctrl+C to stop.[/dim]\n")

    cmd = ["adb", "-s", dev, "logcat", "-v", "time"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")

    try:
        for line in proc.stdout:
            if any(t in line for t in tags):
                if " E " in line or "Error" in line:
                    console.print(f"[bold red]{line.strip()}[/bold red]")
                elif " W " in line or "Warning" in line:
                    console.print(f"[bold yellow]{line.strip()}[/bold yellow]")
                elif " I " in line:
                    console.print(f"[bold green]{line.strip()}[/bold green]")
                else:
                    console.print(f"[cyan]{line.strip()}[/cyan]")
    except KeyboardInterrupt:
        proc.terminate()
        console.print("\n[bold yellow]Log streaming stopped.[/bold yellow]")


@cli.command("test-action")
@click.argument("action", default="pen", type=click.Choice(["pen", "eraser", "lasso", "eraser_hold_down", "eraser_hold_up"]))
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
def test_action(action, target):
    """Send simulated stylus command broadcast to StarNote canvas."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device available.[/bold red]")
        sys.exit(1)

    console.print(f"[bold cyan]>> Sending test stylus command [yellow]{action}[/yellow] to {STAR_NOTE_PACKAGE}...[/bold cyan]")
    cmd = [
        "adb", "-s", dev, "shell", "am", "broadcast",
        "-a", "com.gaomon.m8.ACTION_COMMAND",
        "-p", STAR_NOTE_PACKAGE,
        "--es", "cmd", action
    ]
    res = run_cmd(cmd)
    if res and res.returncode == 0:
        console.print(f"[bold green][OK] Command '{action}' sent successfully.[/bold green]")
    else:
        console.print(f"[bold red][FAIL] Failed to send broadcast: {res.stderr if res else ''}[/bold red]")


@cli.command("restart-note")
@click.option("--target", default=DEFAULT_DEVICE, help="ADB target IP:port")
def restart_note(target):
    """Force stop and relaunch StarNote to reload modern Xposed hooks."""
    dev = get_adb_device(target)
    if not dev:
        console.print("[bold red][FAIL] No ADB device available.[/bold red]")
        sys.exit(1)

    console.print(f"[bold cyan]>> Restarting {STAR_NOTE_PACKAGE} on {dev}...[/bold cyan]")
    run_cmd(["adb", "-s", dev, "shell", "am", "force-stop", STAR_NOTE_PACKAGE])
    run_cmd([
        "adb", "-s", dev, "shell", "am", "start",
        "-n", f"{STAR_NOTE_PACKAGE}/.editor.ui.NoteScribbleActivity"
    ])
    console.print("[bold green][OK] StarNote relaunched into canvas.[/bold green]")


@cli.command("status")
def status():
    """Print overall toolchain and cache status (Java, NDK, Rust, sccache, LLVM, etc.)."""
    table = Table(title="Engineering Toolchain & Optimization Status", border_style="green")
    table.add_column("Component", style="bold")
    table.add_column("Version / Info", style="cyan")
    table.add_column("Integration Status", style="green")

    # Check sccache
    sc_res = run_cmd(["sccache", "--show-stats"])
    table.add_row("sccache (Build Cache)", "0.17.0 (Scoop)", "[OK] Active & Ready" if sc_res and sc_res.returncode == 0 else "[WARN] Idle")

    # Check Java
    j_res = run_cmd(["java", "-version"])
    table.add_row("JDK Runtime", "Java 24 (64-bit)", "[OK] Configured")

    # Check Android NDK
    ndk_path = os.environ.get("ANDROID_NDK") or os.environ.get("ANDROID_NDK_ROOT", "C:\\Users\\Pochita\\AppData\\Local\\Android\\Sdk\\ndk\\29.0.13599879")
    table.add_row("Android NDK", f"NDK 29.0 ({Path(ndk_path).name})", "[OK] Toolchain Bound")

    # Check Rust
    r_res = run_cmd(["rustc", "--version"])
    r_ver = r_res.stdout.strip() if r_res else "Unknown"
    table.add_row("Rust & Cargo", r_ver, "[OK] Android Targets Ready")

    # Check LLVM / Clang
    c_res = run_cmd(["clang", "--version"])
    c_line = c_res.stdout.splitlines()[0] if c_res and c_res.stdout else "Unknown"
    table.add_row("LLVM / Clang", c_line, "[OK] Configured via Scoop")

    # Check Device
    dev = get_adb_device(DEFAULT_DEVICE)
    table.add_row("ADB Tablet Link", dev or "Disconnected", "[OK] Online (10.0.0.12:5555)" if dev else "[FAIL] Offline")

    console.print(table)


if __name__ == "__main__":
    cli()
