from pathlib import Path
import re

source = Path(
    "composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/inference/InferencePlatform.jvm.kt"
).read_text()

for function in ("getAvailableMemoryBytes", "getTotalMemoryBytes"):
    assert re.search(rf"actual fun {function}\(\): Long =", source), f"Could not find implementation of {function}"
    assert f"actual fun {function}(): Long = Long.MAX_VALUE" not in source, (
        f"{function} is hard-coded to Long.MAX_VALUE; the LiteRT load guard sees "
        "unbounded memory and cannot reject a low-memory desktop"
    )

assert "OperatingSystemMXBean" in source, "Desktop implementation must use OS memory measurements"
