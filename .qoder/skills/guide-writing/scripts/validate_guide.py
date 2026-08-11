#!/usr/bin/env python3
"""Deterministic structural checks for Markdown technical guides."""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any


HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
LOCAL_LINK_RE = re.compile(r"\[[^\]]+\]\(#([^)]+)\)")
FENCE_RE = re.compile(r"^\s*(`{3,}|~{3,})")
PLACEHOLDER_PATTERNS = (
    re.compile(r"\b(?:TBD|TODO)\b", re.IGNORECASE),
    re.compile(r"\[(?:主题|待补充|标题|分支\s*[A-Z]|XXX)\]", re.IGNORECASE),
    re.compile(r"\{\{[^{}\n]+\}\}"),
)
TOC_TITLES = {"目录", "table of contents", "contents", "toc"}


def github_slug(text: str) -> str:
    """Return a portable approximation of GitHub's Unicode heading slug."""
    text = re.sub(r"`([^`]*)`", r"\1", text.strip().lower())
    normalized = unicodedata.normalize("NFKC", text)
    kept = []
    for character in normalized:
        category = unicodedata.category(character)
        if character.isspace() or character == "-":
            kept.append("-")
        elif category[0] in {"L", "N"} or character == "_":
            kept.append(character)
    return re.sub(r"-+", "-", "".join(kept)).strip("-")


def collect_structure(lines: list[str]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], int]:
    headings: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    open_fence: tuple[str, int, int] | None = None
    fence_count = 0

    for number, line in enumerate(lines, start=1):
        fence_match = FENCE_RE.match(line)
        if fence_match:
            marker = fence_match.group(1)
            fence_count += 1
            if open_fence is None:
                open_fence = (marker[0], len(marker), number)
            elif marker[0] == open_fence[0] and len(marker) >= open_fence[1]:
                open_fence = None
            continue
        if open_fence is not None:
            continue
        heading_match = HEADING_RE.match(line)
        if heading_match:
            headings.append(
                {
                    "level": len(heading_match.group(1)),
                    "title": heading_match.group(2).strip(),
                    "line": number,
                }
            )

    if open_fence is not None:
        errors.append(
            {
                "code": "unbalanced-code-fence",
                "line": open_fence[2],
                "message": "Code fence opened here is not closed.",
            }
        )

    slug_counts: defaultdict[str, int] = defaultdict(int)
    for heading in headings:
        base = github_slug(heading["title"])
        suffix = slug_counts[base]
        slug_counts[base] += 1
        heading["anchor"] = base if suffix == 0 else f"{base}-{suffix}"

    return headings, errors, fence_count


def validate(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        return {
            "path": str(path),
            "valid": False,
            "errors": [{"code": "read-error", "line": None, "message": str(error)}],
            "warnings": [],
            "metrics": {},
        }

    lines = text.splitlines()
    headings, errors, fence_count = collect_structure(lines)
    h1_headings = [heading for heading in headings if heading["level"] == 1]
    h2_headings = [heading for heading in headings if heading["level"] == 2]

    if len(h1_headings) != 1:
        errors.append(
            {
                "code": "h1-count",
                "line": None,
                "message": f"Expected exactly one H1 heading, found {len(h1_headings)}.",
            }
        )

    toc_headings = [
        heading for heading in h2_headings if heading["title"].strip().lower() in TOC_TITLES
    ]
    content_h2_count = len(h2_headings) - len(toc_headings)
    if content_h2_count >= 4 and not toc_headings:
        errors.append(
            {
                "code": "toc-required",
                "line": None,
                "message": "Guides with four or more H2 content sections require a table of contents.",
            }
        )

    anchors = {heading["anchor"] for heading in headings}
    toc_links: list[tuple[str, int]] = []
    if toc_headings:
        toc_start = toc_headings[0]["line"]
        next_h2_lines = [
            heading["line"]
            for heading in h2_headings
            if heading["line"] > toc_start
        ]
        toc_end = min(next_h2_lines) if next_h2_lines else len(lines) + 1
        for line_number in range(toc_start + 1, toc_end):
            for target in LOCAL_LINK_RE.findall(lines[line_number - 1]):
                toc_links.append((target, line_number))
                if target not in anchors:
                    errors.append(
                        {
                            "code": "toc-target-missing",
                            "line": line_number,
                            "message": f"TOC target '#{target}' does not match any heading anchor.",
                        }
                    )

    for line_number, line in enumerate(lines, start=1):
        for pattern in PLACEHOLDER_PATTERNS:
            if pattern.search(line):
                errors.append(
                    {
                        "code": "template-placeholder",
                        "line": line_number,
                        "message": f"Unresolved template placeholder: {line.strip()}",
                    }
                )
                break

    return {
        "path": str(path),
        "valid": not errors,
        "errors": errors,
        "warnings": [],
        "metrics": {
            "h1_count": len(h1_headings),
            "h2_content_count": content_h2_count,
            "toc_link_count": len(toc_links),
            "code_fence_markers": fence_count,
        },
    }


def render_text(payload: dict[str, Any]) -> str:
    if payload["valid"]:
        metrics = payload["metrics"]
        return (
            f"PASS {payload['path']} "
            f"(H2 sections: {metrics['h2_content_count']}, "
            f"TOC links: {metrics['toc_link_count']})"
        )
    lines = [f"FAIL {payload['path']}"]
    for error in payload["errors"]:
        location = f"line {error['line']}: " if error["line"] else ""
        lines.append(f"- [{error['code']}] {location}{error['message']}")
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("guide", type=Path, help="Markdown guide to validate")
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="Diagnostic output format",
    )
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    payload = validate(args.guide)
    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print(render_text(payload))
    return 0 if payload["valid"] else 1


if __name__ == "__main__":
    sys.exit(main())
