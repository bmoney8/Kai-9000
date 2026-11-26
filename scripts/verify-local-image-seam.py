#!/usr/bin/env python3
"""Static contract checks for the local image-generation provider seam (issue #23).

No JDK, Gradle, GPU, or native toolchain is required. This script performs
repository-scoped source inspections only; it does not build or run any tests.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures = []


def check(name, condition):
    print(("PASS" if condition else "FAIL") + f": {name}")
    if not condition:
        failures.append(name)


image_dir = {
    "common": ROOT / "composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/inference/image",
    "android": ROOT / "composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/inference/image",
    "desktop": ROOT / "composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/inference/image",
    "ios": ROOT / "composeApp/src/iosMain/kotlin/com/inspiredandroid/kai/inference/image",
    "wasm": ROOT / "composeApp/src/wasmJsMain/kotlin/com/inspiredandroid/kai/inference/image",
}
provider = image_dir["common"] / "LocalImageGenerationEngineProvider.kt"
check("expect provider declared in commonMain", provider.is_file() and "expect fun createLocalImageGenerationEngine" in provider.read_text())
suffixes = {"android": "android", "desktop": "jvm", "ios": "ios", "wasm": "wasmJs"}
for target, suffix in suffixes.items():
    path = image_dir[target] / f"LocalImageGenerationEngineProvider.{suffix}.kt"
    check(f"actual provider returns null on {target}", path.is_file() and "actual fun createLocalImageGenerationEngine(): LocalImageGenerationEngine? = null" in path.read_text())

source = (image_dir["common"] / "LocalImageGeneration.kt").read_text()
check("unload runs in finally", "finally {\n        engine.unload()" in source)
check("no HTTP(S) endpoints in seam", "https://" not in source and "http://" not in source)
check("no discrete-GPU backend enum", not re.search(r"^\s*DISCRETE\w*,?\s*$", source, re.MULTILINE))
check("AVX2_CPU backend defined", "AVX2_CPU" in source)
check("whitelist families present", all(k in source for k in ("SDXS_512", "SDXL_LIGHTNING", "STEP_DISTILLED_SD_Q4", "FLUX_2_KLEIN_4B_Q4_K_M")))
check("FLUX benchmark-only flag", "benchmarkOnly = true" in source)

tests = ROOT / "composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/inference/image/LocalImageGenerationTest.kt"
check("unload-covered test file exists", tests.is_file() and tests.read_text().count("unload") >= 6)

docs = (ROOT / "docs/features/local-image-generation.md").read_text()
check("benchmark matrix cells marked not measured", docs.count("not measured") >= 10)
check("latency described as stretch target only", "stretch target" in docs and "not a" in docs)

kt_files = list(image_dir["common"].glob("*.kt")) + list(image_dir["android"].glob("*.kt")) + list(image_dir["desktop"].glob("*.kt")) + list(image_dir["ios"].glob("*.kt")) + list(image_dir["wasm"].glob("*.kt")) + [tests]
overlong = [(p, line) for p in kt_files for line in p.read_text().splitlines() if len(line) > 140]
check("no .kt lines over 140 chars", not overlong)

trailing_ws = [str(p) for p in list(image_dir["common"].glob("*.kt")) + [tests]
               if re.search(r"[ \t]+$", p.read_text(), re.MULTILINE)]
check("no trailing whitespace in new .kt sources", not trailing_ws)

print(f"\n{len(failures)} failure(s).")
sys.exit(1 if failures else 0)
