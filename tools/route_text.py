"""Exact catalog interpretation for experiment outputs; never reads expected labels."""

import json
import re
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "data" / "iloilo-jaro-wvsu.json"
TRUTH_IDS = {"ROUTE-03": "ILO-03", "ROUTE-10": "ILO-10"}


def normalize_text(text):
    text = text.lower()
    return " ".join("".join(c if c.isalnum() else " " for c in text).split())


def interpret(text, routes=None):
    if routes is None:
        routes = json.loads(CATALOG.read_text(encoding="utf-8"))["routes"]
    printed = list(dict.fromkeys(re.findall(r"(?<![^\W_])\d+(?![^\W_])", text)))
    codes = list(dict.fromkeys(code.lstrip("0") or "0" for code in printed))
    normalized = f" {normalize_text(text)} "
    candidates, known = [], set()
    for route in routes:
        aliases = [route["name"], *route.get("aliases", [])]
        numbers = {code.lstrip("0") or "0" for alias in aliases
                   for code in re.findall(r"\broute\s+(\d+)\b", alias, re.I)}
        known.update(numbers)
        if numbers.intersection(codes) or any(
                normalize_text(alias) and f" {normalize_text(alias)} " in normalized for alias in aliases):
            candidates.append(route["id"])
    status = ("ambiguous" if len(candidates) > 1 or candidates and set(codes) - known else
              "found" if candidates else "unsupported" if codes else "unreadable")
    return {"extraction_status": "completed", "printed_codes": printed, "normalized_codes": codes,
            "route_status": status, "candidate_route_ids": candidates,
            "canonical_route_id": candidates[0] if status == "found" else None}


def canonical_truth(route_id):
    return TRUTH_IDS.get(route_id, route_id if route_id in TRUTH_IDS.values() else None)
