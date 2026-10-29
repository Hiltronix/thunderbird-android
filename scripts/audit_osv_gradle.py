#!/usr/bin/env python3
"""Inventory resolved Gradle Maven dependencies and query the OSV advisory service.

Usage:
    python3 scripts/audit_osv_gradle.py resolved-foss-release-dependencies.txt

Exit 0 means the query completed, not that the package is vulnerability-free.
The JSON artifact records every advisory to review and triage.
"""
import json
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


COORDINATES = re.compile(
    r"(?<![A-Za-z0-9_.-])([A-Za-z][A-Za-z0-9_.-]*):"
    r"([A-Za-z0-9_.-]+):([vV]?[0-9][A-Za-z0-9_.+\-]*)"
    r"(?:\s+->\s+([vV]?[0-9][A-Za-z0-9_.+\-]*))?"
)


def parse_dependencies(text):
    found = {}
    for line in text.splitlines():
        match = COORDINATES.search(line)
        if not match or "FAILED" in line:
            continue
        group, artifact, declared, resolved = match.groups()
        version = resolved or declared
        found[(group, artifact)] = version
    return [
        {"name": f"{group}:{artifact}", "version": version}
        for (group, artifact), version in sorted(found.items())
    ]


def query_osv(dependencies):
    findings = []
    for offset in range(0, len(dependencies), 100):
        batch = dependencies[offset:offset + 100]
        query = {
            "queries": [
                {"package": {"name": dep["name"], "ecosystem": "Maven"}, "version": dep["version"]}
                for dep in batch
            ]
        }
        request = Request(
            "https://api.osv.dev/v1/querybatch",
            data=json.dumps(query).encode("utf-8"),
            headers={"Content-Type": "application/json", "User-Agent": "hiltronix-thunderbird-security-audit"},
            method="POST",
        )
        for attempt in range(3):
            try:
                with urlopen(request, timeout=90) as response:
                    answer = json.load(response)
                break
            except (HTTPError, URLError, TimeoutError):
                if attempt == 2:
                    raise
                time.sleep(2 ** attempt)
        results = answer["results"]
        if len(results) != len(batch):
            raise RuntimeError("OSV returned an unexpected number of results")
        for dependency, result in zip(batch, results):
            vulns = result.get("vulns", [])
            if vulns:
                findings.append({
                    **dependency,
                    "advisories": [vuln.get("id", "unknown") for vuln in vulns],
                })
    return findings


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: audit_osv_gradle.py <gradle-dependencies.txt>")
    source = Path(sys.argv[1])
    dependencies = parse_dependencies(source.read_text(encoding="utf-8"))
    if len(dependencies) < 50:
        raise RuntimeError(f"Only {len(dependencies)} coordinates parsed; expected a resolved release dependency tree")

    findings = query_osv(dependencies)
    report = {
        "source": str(source),
        "dependencies_scanned": len(dependencies),
        "packages_with_advisories": len(findings),
        "findings": findings,
        "caveat": "Advisory matches require human review; compilation and static scans do not prove exploitability.",
    }
    target = Path("thunderbird-osv-security-report.json")
    target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Scanned {len(dependencies)} resolved Maven dependencies.")
    print(f"Found {len(findings)} packages with one or more OSV advisories.")
    for finding in findings:
        print(f"  {finding['name']}:{finding['version']}: {', '.join(finding['advisories'])}")
    print(f"Detailed report: {target}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"::error::OSV dependency scan failed: {error}", file=sys.stderr)
        raise SystemExit(1)
