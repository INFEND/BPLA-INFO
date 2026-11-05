#!/usr/bin/env python3
"""Deterministically exclude sea and ground models from the APK.

The unchanged source is archived outside app/src; no user photos are modified.
"""
import argparse
import collections
import gzip
import hashlib
import json
from pathlib import Path

ALLOWED = {"Самолётный": 103, "Коптерный": 11, "Барражирующий": 6}
EXCLUDED = {"Морской": 15, "Наземный": 11}


def filter_album(original):
    document = json.loads(gzip.decompress(original).decode("utf-8"))
    if (document.get("schema") != 1 or document.get("photosImported") != 0
            or document.get("operationsImported") != 0):
        raise ValueError("Unexpected album schema or unsafe fields")
    categories = collections.Counter(item["type"] for item in document["entries"])
    if categories != collections.Counter(ALLOWED) + collections.Counter(EXCLUDED):
        raise ValueError("Original source category totals changed: " + repr(categories))
    retained = [item for item in document["entries"] if item["type"] in ALLOWED]
    if len(retained) != 120:
        raise ValueError("Unexpected retained aircraft count")
    document["entries"] = retained
    document["excludedCategories"] = EXCLUDED
    document["sourceArchiveSha256"] = hashlib.sha256(original).hexdigest()
    return gzip.compress(json.dumps(document, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8"), mtime=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    result = filter_album(Path(args.input).read_bytes())
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(result)
    print("PASS: 26 non-aircraft profiles excluded; 120 aviation profiles generated:", len(result), "bytes")


if __name__ == "__main__":
    main()
