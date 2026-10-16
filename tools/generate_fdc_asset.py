"""Build per-100g macros from official FDC CSV directories or JSON/CSV ZIPs.

Only primary food records are imported, never laboratory/sample records. Energy
uses Atwater Specific (2048), then Atwater General (2047), then legacy kcal (1008).
Missing nutrients are not replaced by zero or estimated from other nutrients.
"""

import argparse
import csv
import gzip
import io
import json
import math
from collections import Counter
from contextlib import ExitStack
from pathlib import Path
from zipfile import ZipFile


PRIMARY_TYPES = {"foundation_food", "sr_legacy_food", "survey_fndds_food"}
NUTRIENTS = {"1003", "1004", "1005", "1008", "2047", "2048"}
ENERGY_PRIORITY = ("2048", "2047", "1008")


def csv_records(open_table):
    nutrients = {}
    with open_table("food_nutrient.csv") as handle:
        for row in csv.DictReader(handle):
            key = row["nutrient_id"]
            if key in NUTRIENTS:
                nutrients.setdefault(row["fdc_id"], {})[key] = row["amount"]
    with open_table("food.csv") as handle:
        for food in csv.DictReader(handle):
            if food["data_type"] in PRIMARY_TYPES:
                yield food["fdc_id"], food["description"], nutrients.get(food["fdc_id"], {})


def json_records(payload):
    for key in ("FoundationFoods", "SRLegacyFoods", "SurveyFoods"):
        for food in payload.get(key, []):
            if not isinstance(food, dict):
                continue
            nutrients = {
                str(entry.get("nutrient", {}).get("id")): entry.get("amount")
                for entry in food.get("foodNutrients", [])
            }
            yield str(food["fdcId"]), food["description"], nutrients


def records(source: Path):
    if source.is_dir():
        yield from csv_records(lambda name: (source / name).open(encoding="utf-8-sig", newline=""))
    elif source.suffix.lower() == ".json":
        with source.open(encoding="utf-8-sig") as handle:
            yield from json_records(json.load(handle))
    else:
        with ZipFile(source) as archive:
            json_names = [name for name in archive.namelist() if name.endswith(".json")]
            if json_names:
                if len(json_names) != 1:
                    raise ValueError("Expected one food JSON file")
                with archive.open(json_names[0]) as handle:
                    yield from json_records(json.load(handle))
            else:
                def open_table(name):
                    matches = [item for item in archive.namelist() if Path(item).name == name]
                    if len(matches) != 1:
                        raise ValueError(f"Expected exactly one {name}")
                    return io.TextIOWrapper(archive.open(matches[0]), encoding="utf-8-sig", newline="")
                yield from csv_records(open_table)


def valid_number(value):
    try:
        return value is not None and math.isfinite(float(value)) and float(value) >= 0
    except (TypeError, ValueError):
        return False


def main(source: Path, output: Path):
    rows = []
    seen = set()
    energy_counts = Counter()
    skipped = 0
    for food_id, description, nutrients in records(source):
        energy_id = next((key for key in ENERGY_PRIORITY if valid_number(nutrients.get(key))), None)
        if energy_id is None or not all(valid_number(nutrients.get(key)) for key in ("1003", "1005", "1004")):
            skipped += 1
            continue
        if food_id in seen:
            raise ValueError(f"Duplicate FDC ID: {food_id}")
        seen.add(food_id)
        energy_counts[energy_id] += 1
        rows.append([food_id, description.replace("\t", " ").replace("\n", " ").replace("\r", " "),
                     *[str(nutrients[key]) for key in (energy_id, "1003", "1005", "1004")]])
    if not rows:
        raise ValueError("No foods with complete macros; output not written")
    rows.sort(key=lambda row: int(row[0]))
    output.parent.mkdir(parents=True, exist_ok=True)
    with ExitStack() as stack:
        raw = stack.enter_context(output.open("wb"))
        compressed = stack.enter_context(gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0))
        target = stack.enter_context(io.TextIOWrapper(compressed, encoding="utf-8", newline=""))
        writer = csv.writer(target, delimiter="\t", lineterminator="\n")
        writer.writerow(["fdc_id", "description", "calories_kcal", "protein_g", "carbs_g", "fat_g"])
        writer.writerows(rows)
    summary = {"written": len(rows), "skipped_incomplete": skipped, "energy_nutrient_counts": dict(energy_counts)}
    print(json.dumps(summary, sort_keys=True), output)
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    arguments = parser.parse_args()
    main(arguments.source, arguments.output)
