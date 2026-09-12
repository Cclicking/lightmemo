"""Build the compact fallback database from Sanotsu/china-food-composition-data."""

import csv
import gzip
import sys
from pathlib import Path


def number(value: str) -> float:
    value = value.strip()
    if value.lower() == "tr":
        return 0.0
    # The source marks several measured oil energy values with a trailing footnote '*'.
    return float(value.rstrip("*"))


def main(source_csv: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with source_csv.open(encoding="utf-8-sig", newline="") as source, gzip.open(
        output, "wt", encoding="utf-8", newline=""
    ) as target:
        writer = csv.writer(target, delimiter="\t", lineterminator="\n")
        writer.writerow(["food_code", "food_name", "english_name", "calories_kcal", "protein_g", "carbs_g", "fat_g"])
        for row in csv.DictReader(source):
            try:
                macros = [number(row[key]) for key in ("energyKCal", "protein", "CHO", "fat")]
            except (KeyError, ValueError):
                continue
            writer.writerow(
                [
                    row["foodCode"],
                    row["foodName"].replace("\t", " ").replace("\n", " "),
                    row["englishName"].replace("\t", " ").replace("\n", " "),
                    *macros,
                ]
            )
            written += 1
    print(f"wrote {written} foods to {output}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
