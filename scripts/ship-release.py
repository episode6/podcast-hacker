#!/usr/bin/env python3
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

XCCONFIG_PATH = "iosApp/Configuration/Config.xcconfig"

def get_current_branch():
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error getting current git branch: {e.stderr.strip()}", file=sys.stderr)
        sys.exit(1)

def gradle_version_code(task):
    # the versionCode formula lives in the root build.gradle.kts (the single source of
    # truth); query it via the print tasks it registers
    result = subprocess.run(
        ["./gradlew", "-q", task],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    if result.returncode != 0:
        print(f"Error running ./gradlew {task}: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(1)
    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not lines or not lines[-1].isdigit():
        print(f"Error: unexpected ./gradlew {task} output: {result.stdout.strip()!r}", file=sys.stderr)
        sys.exit(1)
    return lines[-1]

def get_version():
    version_file = "self.versions.toml"
    if not os.path.exists(version_file):
        print(f"Error: {version_file} not found in the current directory.", file=sys.stderr)
        sys.exit(1)

    with open(version_file, "r") as f:
        content = f.read()

    # name is the single version source of truth; the release versionCode / iOS
    # build number is derived from it (this also validates the name's segments)
    name_match = re.search(r'name\s*=\s*"([^"]+)"', content)
    if not name_match:
        print("Error: Could not find name version pattern in self.versions.toml", file=sys.stderr)
        sys.exit(1)

    version = name_match.group(1)
    code = gradle_version_code("printReleaseVersionCode")

    return version, code

def check_xcconfig_in_sync(version):
    if not os.path.exists(XCCONFIG_PATH):
        print(f"Error: {XCCONFIG_PATH} not found.", file=sys.stderr)
        sys.exit(1)

    with open(XCCONFIG_PATH, "r") as f:
        content = f.read()

    marketing = re.search(r"^MARKETING_VERSION=(.*)$", content, re.MULTILINE)
    project = re.search(r"^CURRENT_PROJECT_VERSION=(.*)$", content, re.MULTILINE)
    marketing_version = marketing.group(1).strip() if marketing else None
    project_version = project.group(1).strip() if project else None

    # The committed CURRENT_PROJECT_VERSION is always the pinned iOS snapshot build
    # number — CI swaps in the release code on tag builds (sync-ios-version.sh
    # --release), so that swap must never be committed.
    expected_marketing = version
    expected_project = gradle_version_code("printSnapshotVersionCode")
    if marketing_version != expected_marketing or project_version != expected_project:
        print(
            f"Error: {XCCONFIG_PATH} (MARKETING_VERSION={marketing_version}, "
            f"CURRENT_PROJECT_VERSION={project_version}) is out of sync with "
            f"self.versions.toml (name={version}, expected MARKETING_VERSION={expected_marketing}, "
            f"expected CURRENT_PROJECT_VERSION={expected_project}). "
            "Run scripts/sync-ios-version.sh and commit the result.",
            file=sys.stderr
        )
        sys.exit(1)

def get_changelog_notes(version):
    changelog_file = "CHANGELOG.md"
    if not os.path.exists(changelog_file):
        print(f"Error: {changelog_file} not found.", file=sys.stderr)
        sys.exit(1)

    with open(changelog_file, "r") as f:
        lines = f.readlines()

    notes = []
    found_section = False

    # We look for a line starting with '### v<version>'
    header_pattern = re.compile(rf"^###\s+v{re.escape(version)}(\s+|$|-)")
    any_header_pattern = re.compile(r"^###\s+v\d+")

    for line in lines:
        if found_section:
            if any_header_pattern.match(line):
                break
            notes.append(line)
        elif header_pattern.match(line):
            found_section = True

    if not found_section:
        print(f"Error: Could not find changelog section for v{version} in CHANGELOG.md", file=sys.stderr)
        sys.exit(1)

    content = "".join(notes).strip()
    if not content:
        print(f"Warning: Changelog notes for v{version} are empty.", file=sys.stderr)

    return content

def check_release_branch(branch, dry_run):
    # main always carries a releasable-looking plain version in this repo, so guard
    # against accidentally shipping from anything but a release branch
    if not branch.startswith("release/"):
        message = f"Target branch '{branch}' is not a release/* branch."
        if dry_run:
            print(f"[DRY-RUN] Warning: {message}", file=sys.stderr)
        else:
            print(f"Error: {message} Releases must ship from a release branch.", file=sys.stderr)
            sys.exit(1)

def run_gh_release(version, code, notes, target_branch, dry_run=False):
    tag_name = f"v{version}"
    release_name = f"v{version}"

    # Create temp file for release notes
    with tempfile.NamedTemporaryFile(mode='w+', suffix='.md', delete=False) as temp_notes:
        temp_notes.write(notes)
        temp_notes_path = temp_notes.name

    try:
        cmd = [
            "gh", "release", "create", tag_name,
            "--title", release_name,
            "--notes-file", temp_notes_path,
            "--target", target_branch
        ]

        if dry_run:
            print("[DRY-RUN] Would execute command:")
            print(" ".join(cmd))
            print("\n[DRY-RUN] Release Notes:")
            print("-" * 40)
            print(notes)
            print("-" * 40)
            return {
                "success": True,
                "dry_run": True,
                "tag": tag_name,
                "title": release_name,
                "version_code": code,
                "branch": target_branch,
                "command": " ".join(cmd),
                "notes": notes
            }
        else:
            print(f"Running: {' '.join(cmd)}")
            result = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=True
            )
            release_url = result.stdout.strip()
            print(f"Success! Created release: {release_url}")
            print("The tag push now triggers build-installers.yml, which attaches the installers/APK to this release.")
            return {
                "success": True,
                "dry_run": False,
                "tag": tag_name,
                "title": release_name,
                "version_code": code,
                "branch": target_branch,
                "url": release_url,
                "notes": notes
            }
    except subprocess.CalledProcessError as e:
        print(f"Error executing gh release: {e.stderr.strip()}", file=sys.stderr)
        sys.exit(1)
    finally:
        if os.path.exists(temp_notes_path):
            os.remove(temp_notes_path)

def main():
    parser = argparse.ArgumentParser(description="Ship a release branch by publishing it on GitHub.")
    parser.add_argument("--branch", help="Target branch/ref to point the release to (defaults to current branch)")
    parser.add_argument("--dry-run", action="store_true", help="Print details of the release without publishing")
    parser.add_argument("--output", help="Optional path to write a JSON report of the release results")

    args = parser.parse_args()

    # Require --output argument to comply with Rule 3 (CLI Script Pattern)
    if not args.output:
        print("Error: --output <file_path> is required to capture the execution results.", file=sys.stderr)
        sys.exit(1)

    branch = args.branch if args.branch else get_current_branch()
    check_release_branch(branch, args.dry_run)
    version, code = get_version()
    check_xcconfig_in_sync(version)
    notes = get_changelog_notes(version)

    result = run_gh_release(version, code, notes, branch, dry_run=args.dry_run)

    # Write JSON output report
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)

    print(f"Execution results written to: {args.output}")

if __name__ == "__main__":
    main()
