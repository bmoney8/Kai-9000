"""Source-level regression probe for spotlessCheck ordering before unit tests."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class SpotlessTaskDependencyTest(unittest.TestCase):
    def test_unit_test_tasks_depend_on_spotless_check(self):
        root_build = (ROOT / "build.gradle.kts").read_text()
        self.assertRegex(
            root_build,
            re.compile(
                r"tasks\.withType<Test>\(\)\.configureEach\s*\{\s*"
                r"dependsOn\(rootProject\.tasks\.named\(\"spotlessCheck\"\)\)"
            ),
            "No dependency wires all unit-test tasks to the root spotlessCheck task",
        )

    def test_spotless_version_is_812(self):
        catalog = (ROOT / "gradle/libs.versions.toml").read_text()
        self.assertRegex(catalog, r'(?m)^spotless = "8\.12\.0"$')


if __name__ == "__main__":
    unittest.main()
