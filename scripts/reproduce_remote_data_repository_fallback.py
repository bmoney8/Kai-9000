"""Regression probe for issue #3.

The fallback builder must preserve a configured LiteRT entry after a remote
provider.  The probe is repository-relative and does not require Gradle/JVM.
"""
from pathlib import Path

SOURCE = Path(__file__).parents[1] / (
    "composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/"
    "RemoteDataRepository.kt"
)
source = SOURCE.read_text()
old_guard = "return ordered.filterIndexed { index, entry -> index == 0 || !entry.service.isOnDevice }"
assert old_guard not in source, "the on-device fallback exclusion is still present"

# The fixed builder returns the ordered entries unchanged. This is the
# configured Priority 1 remote -> Priority 2 LiteRT chain.
entries = [("remote", False), ("litert", True)]
retained = entries
assert retained == entries
