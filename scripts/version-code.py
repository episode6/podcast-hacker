#!/usr/bin/env python3
"""Compute the android versionCode / iOS build number from a version name.

The version name (self.versions.toml) is MAJOR.MINOR.PATCH plus an optional 4th
HOTFIX segment used only when hotfixing a shipped release. The code is derived by
concatenating MAJOR | MINOR(3 digits) | PATCH(3 digits) | HOTFIX(1 digit), e.g.
1.2.3 -> 10020030 and 1.2.3.4 -> 10020034, so newer versions always outrank
older ones and hotfixes slot between patches.

Limits: minor/patch max out at 999, hotfix at 9, and major must be >= 1
(jpackage rejects MAJOR==0 for dmg/msi). The major has no fixed max — the code
just has to fit a 32-bit int, which allows majors up to 214.

The root build.gradle.kts mirrors this formula for gradle builds; keep the two
in sync.

Usage: version-code.py <version-name>
"""
import re
import sys

INT32_MAX = 2_147_483_647


def compute(name):
    segments = name.split(".")
    if len(segments) not in (3, 4):
        raise ValueError(f"version name '{name}' must be MAJOR.MINOR.PATCH with an optional 4th hotfix segment")
    if not all(re.fullmatch(r"\d+", segment) for segment in segments):
        raise ValueError(f"version name '{name}' has a non-numeric segment")
    nums = [int(segment) for segment in segments]
    major, minor, patch = nums[:3]
    hotfix = nums[3] if len(nums) == 4 else 0
    if major < 1:
        raise ValueError("major version must be >= 1 (jpackage rejects MAJOR==0 for dmg/msi)")
    if minor > 999 or patch > 999:
        raise ValueError(f"minor/patch versions max out at 999 (got '{name}')")
    if hotfix > 9:
        raise ValueError(f"hotfix version maxes out at 9 (got '{name}')")
    code = ((major * 1000 + minor) * 1000 + patch) * 10 + hotfix
    if code > INT32_MAX:
        raise ValueError(f"versionCode {code} for '{name}' exceeds the 32-bit int limit; the major version is too large")
    return code


def main():
    if len(sys.argv) != 2:
        print("Usage: version-code.py <version-name>", file=sys.stderr)
        sys.exit(2)
    try:
        print(compute(sys.argv[1]))
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
