#!/usr/bin/env python3
"""Run production telemetry parsers/calculations with valid and unavailable readings."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="winlator-hud-") as temporary:
    compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run([
        *compiler, "-d", temporary,
        str(ROOT / "app/src/main/java/com/winlator/core/HudMetrics.java"),
        str(Path(__file__).with_name("HudMetricsTest.java")),
    ], check=True)
    subprocess.run(["java", "-cp", temporary, "HudMetricsTest"], check=True)
