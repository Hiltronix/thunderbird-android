#!/usr/bin/env python3
"""Generate a Winnipeg-local, daily sequential revision for signed Android builds.

Revision examples: 2026.10.29.1, 2026.10.29.2.
All attempted signed workflow runs count toward the daily build sequence.
"""
import json
import os
import sys
from datetime import datetime
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

ZONE = ZoneInfo("America/Winnipeg")
WORKFLOW_FILE = "build-thunderbird-10.1-signed.yml"
REPOSITORY = "Hiltronix/thunderbird-android"
BRANCH = "thunderbird-10.1-custom"


def daily_revision(runs, local_date, current_run_id, current_attempt):
    """Count workflow attempts initiated on the specified local calendar date."""
    total = 0
    seen_current = False
    for run in runs:
        if run.get("head_branch") != BRANCH:
            continue
        started = datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
        if started.astimezone(ZONE).date() != local_date:
            continue
        attempt = int(run.get("run_attempt", 1))
        total += attempt
        if int(run["id"]) == current_run_id:
            seen_current = True

    # Rerunning a build initially created on a different day still produces a
    # unique revision on the date of the rerun, even though this edge case
    # cannot be counted from the creation-date history alone.
    if not seen_current:
        total += current_attempt
    if not 1 <= total <= 99:
        raise ValueError("Daily build revision must be 1..99; check workflow history")
    return total


def list_workflow_runs(token):
    url = f"https://api.github.com/repos/{REPOSITORY}/actions/workflows/{WORKFLOW_FILE}/runs"
    all_runs = []
    page = 1
    while True:
        query = urlencode({"branch": BRANCH, "per_page": 100, "page": page})
        request = Request(
            f"{url}?{query}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "hiltronix-revision-generator",
            },
        )
        with urlopen(request, timeout=30) as response:
            document = json.load(response)

        runs = document["workflow_runs"]
        all_runs.extend(runs)
        if len(runs) < 100:
            break
        page += 1
        if page > 10:
            raise RuntimeError("Too many workflow runs to establish a daily revision")
    return all_runs


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise RuntimeError("This revision generator is restricted to the Hiltronix repository")
    if os.environ.get("GITHUB_REF") != "refs/heads/" + BRANCH:
        raise RuntimeError("This revision generator is restricted to the custom release branch")
    token = os.environ["GH_TOKEN"]
    current_run_id = int(os.environ["GITHUB_RUN_ID"])
    current_attempt = int(os.environ["GITHUB_RUN_ATTEMPT"])

    today = datetime.now(ZONE).date()
    runs = list_workflow_runs(token)
    revision_number = daily_revision(runs, today, current_run_id, current_attempt)
    revision = f"{today:%Y.%m.%d}.{revision_number}"
    version_code = int(f"{today:%Y%m%d}") * 100 + revision_number
    if version_code > 2_100_000_000:
        raise ValueError("Android versionCode exceeds its supported range")

    print(f"Hiltronix Revision: {revision} (versionCode={version_code})")
    output = os.environ.get("GITHUB_ENV")
    if not output:
        raise RuntimeError("GITHUB_ENV not provided")
    with open(output, "a", encoding="utf-8") as stream:
        stream.write(f"HILTRONIX_REVISION={revision}\n")
        stream.write(f"HILTRONIX_VERSION_CODE={version_code}\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"## Thunderbird 10.1 Hiltronix Revision\n\n**{revision}** — Android versionCode {version_code}\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"::error::Could not generate Hiltronix build revision: {exc}", file=sys.stderr)
        raise SystemExit(1)
