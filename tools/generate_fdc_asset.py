"""Build the compact runtime database from an official FoodData Central CSV export."""

import csv
import gzip
import sys
from pathlib import Path


def main(source: Path, output: Path) -> None:
    nutrients = {}
    with (source / "food_nutrient.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            nutrient_id = row["nutrient_id"]
            if nutrient_id in {"1003", "1004", "1005", "1008"}:
                nutrients.setdefault(row["fdc_id"], {})[nutrient_id] = row["amount"]

    output.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with (source / "food.csv").open(encoding="utf-8-sig", newline="") as foods, gzip.open(
        output, "wt", encoding="utf-8", newline=""
    ) as target:
        writer = csv.writer(target, delimiter="\t", lineterminator="\n")
        writer.writerow(["fdc_id", "description", "calories_kcal", "protein_g", "carbs_g", "fat_g"])
        for food in csv.DictReader(foods):
            values = nutrients.get(food["fdc_id"], {})
            if not all(key in values for key in ("1008", "1003", "1005", "1004")):
                continue
            writer.writerow(
                [
                    food["fdc_id"],
                    food["description"].replace("\t", " ").replace("\n", " "),
                    values["1008"],
                    values["1003"],
                    values["1005"],
                    values["1004"],
                ]
            )
            written += 1
    print(f"wrote {written} foods to {output}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
