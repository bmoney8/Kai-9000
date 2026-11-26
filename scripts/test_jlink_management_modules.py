"""Regression probe for desktop runtime modules needed by memory reporting.

Run from the repository root: python3 scripts/test_jlink_management_modules.py
"""

from pathlib import Path
import re

build_file = Path("composeApp/build.gradle.kts")
inference_source = Path(
    "composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/inference/InferencePlatform.jvm.kt"
)

build = build_file.read_text()
source = inference_source.read_text()

assert "java.lang.management.ManagementFactory" in source, (
    "Update this probe if desktop memory reporting no longer uses ManagementFactory."
)
assert "com.sun.management.OperatingSystemMXBean" in source, (
    "Update this probe if desktop memory reporting no longer uses OperatingSystemMXBean."
)

module_calls = re.findall(r"modules\(([^)]*)\)", build)
declared_modules = {
    module.strip().strip('"')
    for call in module_calls
    for module in call.split(",")
    if module.strip()
}

missing_modules = {"java.management", "jdk.management"} - declared_modules
assert not missing_modules, (
    "The packaged jlink runtime omits module(s) required by "
    "InferencePlatform.jvm.kt: "
    + ", ".join(sorted(missing_modules))
    + ". This causes Settings to fail when it initializes ManagementFactory."
)
