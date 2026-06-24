#!/usr/bin/env python3
"""Local dependency vulnerability scan via the OSV.dev API.

A lightweight, Trivy-free counterpart to the `Scan dependencies` CI workflow, for quick local
checks. It resolves the Gradle runtime dependencies (the libraries bundled into the deployable fat
jar) and queries https://osv.dev for known vulnerabilities (Maven ecosystem).

Usage:
    python3 scripts/scan-dependencies.py                 # scan :app runtimeClasspath
    python3 scripts/scan-dependencies.py --module :app --configuration runtimeClasspath
    python3 scripts/scan-dependencies.py --fail-on HIGH  # exit 1 if a >= HIGH vuln is found

Report-only by default (exit 0) to mirror the CI policy. Needs only Python 3 and network access.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.request

OSV_QUERYBATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
SEVERITY_ORDER = {"LOW": 1, "MEDIUM": 2, "MODERATE": 2, "HIGH": 3, "CRITICAL": 4}

# Matches a Gradle dependency tree line's coordinate: "group:artifact", optional ":version",
# optional "-> resolvedVersion". Internal "project :libs:..." rows are skipped by the caller.
_COORD = re.compile(
    r"^([\w.\-]+):([\w.\-]+)(?::([\w.\-]+))?(?:\s*->\s*([\w.\-]+))?$"
)
_TREE_LINE = re.compile(r"^[ |]*[+\\]---\s+(.*)$")


def resolve_dependencies(module: str, configuration: str) -> list[tuple[str, str]]:
    """Return sorted unique (``group:artifact``, version) from the Gradle dependency tree."""
    cmd = ["./gradlew", "-q", f"{module}:dependencies", "--configuration", configuration]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout)
        sys.stderr.write(result.stderr)
        raise SystemExit(f"gradle dependency resolution failed: {' '.join(cmd)}")

    deps: set[tuple[str, str]] = set()
    for raw in result.stdout.splitlines():
        if "project :" in raw:
            continue
        line = _TREE_LINE.match(raw)
        if not line:
            continue
        body = re.sub(r"\s*\([*cn]\)\s*$", "", line.group(1)).strip()
        coord = _COORD.match(body)
        if not coord:
            continue
        group, artifact, version, resolved = coord.groups()
        version = resolved or version
        if version:
            deps.add((f"{group}:{artifact}", version))
    return sorted(deps)


def _request_json(url: str, payload: dict | None = None, retries: int = 4) -> dict:
    """GET (or POST when ``payload`` is set) JSON with retries for transient network errors.

    OSV.dev can drop connections under many rapid sequential requests, so retry with backoff.
    """
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {"Content-Type": "application/json"} if payload is not None else {}
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, data=data, headers=headers)
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.load(resp)
        except (OSError, ValueError) as error:  # network drop, timeout, malformed body
            last_error = error
            time.sleep(0.5 * (attempt + 1))
    raise last_error if last_error else RuntimeError(f"request failed: {url}")


def query_osv(deps: list[tuple[str, str]]) -> dict[tuple[str, str], list[str]]:
    """Return {(name, version): [vuln_id, ...]} for deps with known vulnerabilities."""
    found: dict[tuple[str, str], list[str]] = {}
    for start in range(0, len(deps), 500):
        chunk = deps[start : start + 500]
        queries = [
            {"package": {"ecosystem": "Maven", "name": name}, "version": version}
            for name, version in chunk
        ]
        results = _request_json(OSV_QUERYBATCH, {"queries": queries}).get("results", [])
        for dep, result in zip(chunk, results):
            ids = [v["id"] for v in result.get("vulns", [])]
            if ids:
                found[dep] = ids
    return found


def summarize(vuln_id: str) -> tuple[str, str, str]:
    """Return (severity, fixed_version, summary) for a vulnerability id (best effort)."""
    try:
        detail = _request_json(OSV_VULN + vuln_id)
    except Exception:
        return ("UNKNOWN", "-", "")
    severity = (detail.get("database_specific", {}) or {}).get("severity", "UNKNOWN")
    fixed = "-"
    for affected in detail.get("affected", []):
        for rng in affected.get("ranges", []):
            for event in rng.get("events", []):
                if "fixed" in event:
                    fixed = event["fixed"]
    aliases = ", ".join(detail.get("aliases", []))
    summary = detail.get("summary", "") or aliases
    return (str(severity).upper(), fixed, summary)


def main() -> int:
    parser = argparse.ArgumentParser(description="Local OSV.dev dependency vulnerability scan.")
    parser.add_argument("--module", default=":app", help="Gradle module (default: :app)")
    parser.add_argument(
        "--configuration",
        default="runtimeClasspath",
        help="Gradle configuration (default: runtimeClasspath)",
    )
    parser.add_argument(
        "--fail-on",
        choices=["LOW", "MEDIUM", "HIGH", "CRITICAL"],
        help="Exit 1 when a vulnerability at or above this severity is found (default: report-only)",
    )
    args = parser.parse_args()

    print(f"Resolving {args.module}:{args.configuration} dependencies ...", file=sys.stderr)
    deps = resolve_dependencies(args.module, args.configuration)
    print(f"Scanning {len(deps)} dependencies against OSV.dev ...", file=sys.stderr)

    vulnerable = query_osv(deps)
    if not vulnerable:
        print(f"\n✅ No known vulnerabilities found in {len(deps)} dependencies.")
        return 0

    rows = []
    worst = 0
    for (name, version), ids in sorted(vulnerable.items()):
        for vuln_id in ids:
            severity, fixed, summary = summarize(vuln_id)
            worst = max(worst, SEVERITY_ORDER.get(severity, 0))
            rows.append((severity, name, version, fixed, vuln_id, summary))

    rows.sort(key=lambda r: SEVERITY_ORDER.get(r[0], 0), reverse=True)
    print(f"\n⚠️  {len(rows)} vulnerabilities in {len(vulnerable)} dependencies:\n")
    print(f"{'SEVERITY':<9} {'PACKAGE':<48} {'INSTALLED':<14} {'FIXED':<14} VULN")
    print("-" * 110)
    for severity, name, version, fixed, vuln_id, summary in rows:
        print(f"{severity:<9} {name:<48} {version:<14} {fixed:<14} {vuln_id}")
        if summary:
            print(f"{'':<9} └ {summary[:96]}")

    if args.fail_on and worst >= SEVERITY_ORDER[args.fail_on]:
        print(f"\n❌ Found vulnerabilities at or above {args.fail_on}.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
