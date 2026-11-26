"""Source-level regression probe for the @Composable annotation on SettingsTabSelector.

PR #5 moved the annotation onto the newly inserted AdminModeToggle and left
SettingsTabSelector bare, which fails compileKotlinDesktop with
"@Composable invocations can only happen from the context of a @Composable
function" errors. Kotlin compilation is not runnable everywhere (CI-less
environments without Java), so this guards the source condition directly.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / "composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SettingsScreen.kt"

COMPOSABLE_ONLY_APIS = (
    "rememberScrollState",
    "remember",
    "mutableStateMapOf",
    "LaunchedEffect",
    "Surface",
    "Row",
)


class SettingsTabSelectorComposableTest(unittest.TestCase):
    def test_settings_tab_selector_is_annotated_composable(self):
        lines = SCREEN.read_text().splitlines()
        decl = next(
            (i for i, line in enumerate(lines) if re.match(r"\s*private fun SettingsTabSelector\s*\(", line)),
            None,
        )
        self.assertIsNotNone(decl, "Could not find 'private fun SettingsTabSelector(' in SettingsScreen.kt")

        prev = decl - 1
        while prev >= 0 and lines[prev].strip() == "":
            prev -= 1
        self.assertEqual(
            "@Composable",
            lines[prev].strip(),
            f"SettingsTabSelector (line {decl + 1}) must carry @Composable; "
            f"found {lines[prev].strip()!r} above it instead",
        )

    def test_settings_tab_selector_actually_needs_the_annotation(self):
        """Keep the annotation check honest: the function really uses composable APIs."""
        lines = SCREEN.read_text().splitlines()
        decl = next(
            (i for i, line in enumerate(lines) if re.match(r"\s*private fun SettingsTabSelector\s*\(", line)),
            None,
        )
        self.assertIsNotNone(decl)
        body = "\n".join(line for line in lines[decl : decl + 80] if not re.match(r"^(private |internal |public )?fun\s+\w+", line))
        missing = [api for api in COMPOSABLE_ONLY_APIS if not re.search(r"\b" + api + r"\b", body)]
        self.assertEqual(
            [],
            missing,
            f"Expected composable-only APIs in SettingsTabSelector body; not found: {missing}",
        )


if __name__ == "__main__":
    unittest.main()
