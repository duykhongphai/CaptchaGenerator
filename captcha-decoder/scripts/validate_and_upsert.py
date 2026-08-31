#!/usr/bin/env python3
"""Validate CAPTCHA label records and atomically upsert them into test_labels."""

from __future__ import annotations

import argparse
import json
import os
import string
import sys
import tempfile
from pathlib import Path
from typing import Any


SPECIALS = set("#$%&@")
TOP_FORBIDDEN_LETTERS = set("gijoy")
RULE_FORBIDDEN_LETTERS = set("gj")
REQUIRED_FIELDS = ("filename", "raw_top_string", "rules", "final_output")
DEFAULT_LABELS = Path(r"C:\Users\nguye\Downloads\images_bgonly\test_labels.json")


class ValidationError(ValueError):
    """Raised when a proposed record cannot belong to this CAPTCHA schema."""


def normalized_key(char: str) -> str:
    return char.lower() if char.isalpha() else char


def validate_rule_key(key: Any) -> None:
    if not isinstance(key, str) or len(key) != 1:
        raise ValidationError(f"Rule key must be one character: {key!r}")
    if key in SPECIALS:
        return
    if key not in string.ascii_lowercase or key in RULE_FORBIDDEN_LETTERS:
        raise ValidationError(
            f"Invalid rule key {key!r}; letters must be lowercase, and g/j are forbidden"
        )


def validate_top_char(char: str) -> None:
    if char in SPECIALS:
        return
    if char not in string.ascii_letters or char.lower() in TOP_FORBIDDEN_LETTERS:
        raise ValidationError(f"Invalid top character: {char!r}")


def validate_record(record: Any) -> dict[str, Any]:
    if not isinstance(record, dict):
        raise ValidationError("Each record must be a JSON object")
    if tuple(record.keys()) != REQUIRED_FIELDS:
        raise ValidationError(
            "Record fields must be exactly filename, raw_top_string, rules, final_output"
        )

    filename = record["filename"]
    raw = record["raw_top_string"]
    rules = record["rules"]
    final = record["final_output"]

    if (
        not isinstance(filename, str)
        or Path(filename).name != filename
        or not filename.lower().endswith(".png")
    ):
        raise ValidationError("filename must be a PNG basename, not a path")
    if not isinstance(raw, str) or len(raw) not in (5, 6):
        raise ValidationError("raw_top_string must be a 5- or 6-character string")
    if not isinstance(rules, dict) or len(rules) != 5:
        raise ValidationError("rules must contain exactly five unique keys")

    for key, value in rules.items():
        validate_rule_key(key)
        if not isinstance(value, str) or len(value) != 1 or not value.isdigit():
            raise ValidationError(f"Rule value for {key!r} must be a one-digit string")

    for char in raw:
        validate_top_char(char)
        key = normalized_key(char)
        if key not in rules:
            raise ValidationError(f"Top character {char!r} has no rule mapping")

    expected = "".join(rules[normalized_key(char)] for char in raw)
    if not isinstance(final, str) or not final.isdigit():
        raise ValidationError("final_output must contain digits only")
    if final != expected:
        raise ValidationError(
            f"final_output mismatch: expected {expected!r}, received {final!r}"
        )
    return record


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def required_projection(record: dict[str, Any]) -> dict[str, Any]:
    return {field: record[field] for field in REQUIRED_FIELDS}


def atomic_write(path: Path, data: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f"{path.name}.",
            suffix=".tmp",
            delete=False,
            newline="\n",
        ) as handle:
            temporary_name = handle.name
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    finally:
        if temporary_name and os.path.exists(temporary_name):
            os.unlink(temporary_name)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate one or more AI label records and atomically upsert them."
    )
    parser.add_argument("--record-file", type=Path, required=True)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument(
        "--review-labels",
        type=Path,
        help="Defaults to review_labels.json next to --labels when that file exists.",
    )
    args = parser.parse_args()

    payload = load_json(args.record_file)
    proposed = payload if isinstance(payload, list) else [payload]
    if not proposed:
        raise ValidationError("Record payload must not be empty")
    validated = [validate_record(record) for record in proposed]
    filenames = [record["filename"] for record in validated]
    if len(filenames) != len(set(filenames)):
        raise ValidationError("Record payload contains duplicate filenames")

    labels = load_json(args.labels)
    if not isinstance(labels, list):
        raise ValidationError("test_labels.json must contain a top-level array")

    review_path = args.review_labels
    if review_path is None:
        candidate = args.labels.with_name("review_labels.json")
        review_path = candidate if candidate.is_file() else None
    reviewed: dict[str, dict[str, Any]] = {}
    if review_path is not None:
        review_data = load_json(review_path)
        if not isinstance(review_data, list):
            raise ValidationError("review_labels.json must contain a top-level array")
        reviewed = {record["filename"]: record for record in review_data}

    positions: dict[str, int] = {}
    for index, existing in enumerate(labels):
        filename = existing.get("filename") if isinstance(existing, dict) else None
        if filename in positions:
            raise ValidationError(f"Existing labels contain duplicate filename {filename!r}")
        if isinstance(filename, str):
            positions[filename] = index

    appended = 0
    replaced = 0
    unchanged = 0
    for record in validated:
        filename = record["filename"]
        canonical = reviewed.get(filename)
        if canonical is not None:
            if required_projection(canonical) != record:
                raise ValidationError(
                    f"Proposed record conflicts with reviewed ground truth for {filename}"
                )
            record_to_store = dict(record)
            record_to_store["reviewed"] = True
        else:
            record_to_store = record

        if filename not in positions:
            positions[filename] = len(labels)
            labels.append(record_to_store)
            appended += 1
            continue

        index = positions[filename]
        existing = labels[index]
        if isinstance(existing, dict) and existing.get("reviewed") is True:
            if required_projection(existing) != record:
                raise ValidationError(
                    f"Refusing to overwrite reviewed record for {filename}"
                )
            unchanged += 1
        elif existing == record_to_store:
            unchanged += 1
        else:
            labels[index] = record_to_store
            replaced += 1

    atomic_write(args.labels, labels)
    reloaded = load_json(args.labels)
    if not isinstance(reloaded, list):
        raise RuntimeError("Post-write verification failed: labels are not an array")
    reload_map = {record["filename"]: record for record in reloaded}
    for record in validated:
        stored = reload_map.get(record["filename"])
        if stored is None or required_projection(stored) != record:
            raise RuntimeError(
                f"Post-write verification failed for {record['filename']}"
            )

    print(
        json.dumps(
            {
                "validated": len(validated),
                "appended": appended,
                "replaced": replaced,
                "unchanged": unchanged,
                "labels_count": len(reloaded),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as error:
        print(f"validation_error: {error}", file=sys.stderr)
        raise SystemExit(2) from None
