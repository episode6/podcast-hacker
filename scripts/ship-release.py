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

def get_version():
    version_file = "self.versions.toml"
    if not os.path.exists(version_file):
        print(f"Error: {version_file} not found in the current directory.", file=sys.stderr)
        sys.exit(1)

    with open(version_file, "r") as f:
        content = f.read()

    # name is the human version, code is the android versionCode / iOS build number;
    # both live in self.versions.toml (the version source of truth for all platforms)
    name_match = re.search(r'name\s*=\s*"([^"]+)"', content)
    code_match = re.search(r'code\s*=\s*"([^"]+)"', content)
    if not name_match or not code_match:
        print("Error: Could not find name/code version pattern in self.versions.toml", file=sys.stderr)
        sys.exit(1)

    version = name_match.group(1)
    code = code_match.group(1)
    if version.endswith("-SNAPSHOT"):
        print(f"Error: Version '{version}' ends with -SNAPSHOT. This repo uses plain versions; resolve/bump first.", file=sys.stderr)
        sys.exit(1)
    if not code.isdigit() or int(code) < 1:
        print(f"Error: versionCode '{code}' in self.versions.toml is not a positive integer.", file=sys.stderr)
        sys.exit(1)

    return version, code

def check_xcconfig_in_sync(version, code):
    if not os.path.exists(XCCONFIG_PATH):
        print(f"Error: {XCCONFIG_PATH} not found.", file=sys.stderr)
        sys.exit(1)

    with open(XCCONFIG_PATH, "r") as f:
        content = f.read()

    marketing = re.search(r"^MARKETING_VERSION=(.*)$", content, re.MULTILINE)
    project = re.search(r"^CURRENT_PROJECT_VERSION=(.*)$", content, re.MULTILINE)
    marketing_version = marketing.group(1).strip() if marketing else None
    project_version = project.group(1).strip() if project else None

    if marketing_version != version or project_version != code:
        print(
            f"Error: {XCCONFIG_PATH} (MARKETING_VERSION={marketing_version}, "
            f"CURRENT_PROJECT_VERSION={project_version}) is out of sync with "
            f"self.versions.toml (name={version}, code={code}). "
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
    check_xcconfig_in_sync(version, code)
    notes = get_changelog_notes(version)

    result = run_gh_release(version, code, notes, branch, dry_run=args.dry_run)

    # Write JSON output report
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)

    print(f"Execution results written to: {args.output}")

if __name__ == "__main__":
    main()
