"""Regression checks for FDC import; run: python -m unittest discover -s tools."""

import csv
import gzip
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from generate_fdc_asset import main


class FdcImportTest(unittest.TestCase):
    def test_csv_filters_sample_records_and_preserves_atwater_and_zero_values(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "food.csv").write_text(
                "fdc_id,data_type,description\n"
                "1,foundation_food,Food with specific energy\n"
                "2,foundation_food,Food with general energy\n"
                "3,sr_legacy_food,Food with legacy energy\n"
                "4,sample_food,Not a published food\n"
                "5,foundation_food,Missing carbohydrate\n", encoding="utf-8")
            rows = [["fdc_id", "nutrient_id", "amount"]]
            for food_id in range(1, 6):
                rows.extend([[food_id, 1003, 0], [food_id, 1004, 0], [food_id, 1008, 100]])
                if food_id != 5:
                    rows.append([food_id, 1005, 25])
            rows.extend([[1, 2048, 95], [1, 2047, 98], [2, 2047, 99]])
            with (root / "food_nutrient.csv").open("w", encoding="utf-8", newline="") as handle:
                csv.writer(handle).writerows(rows)
            output = root / "foods.tsv.gz"
            summary = main(root, output)
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                imported = list(csv.DictReader(handle, delimiter="\t"))
            self.assertEqual([row["fdc_id"] for row in imported], ["1", "2", "3"])
            self.assertEqual([row["calories_kcal"] for row in imported], ["95", "99", "100"])
            self.assertEqual(imported[0]["protein_g"], "0")
            self.assertEqual(summary["skipped_incomplete"], 1)
            first = output.read_bytes()
            main(root, output)
            self.assertEqual(output.read_bytes(), first)

    def test_json_zip_skips_missing_nutrients_and_does_not_convert_kilojoules(self):
        nutrients = [{"nutrient": {"id": key}, "amount": value}
                     for key, value in [(1003, 3), (1004, 0), (1005, 20), (1008, 100)]]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "survey.zip"
            with ZipFile(archive, "w") as output:
                output.writestr("survey.json", json.dumps({"SurveyFoods": [
                    None,
                    {"fdcId": 1, "description": "Complete food", "foodNutrients": nutrients},
                    {"fdcId": 2, "description": "Missing nutrients", "foodNutrients": []},
                    {"fdcId": 3, "description": "Kilojoules only", "foodNutrients":
                        [n for n in nutrients if n["nutrient"]["id"] != 1008] +
                        [{"nutrient": {"id": 1062}, "amount": 418.4}]},
                ]}))
            summary = main(archive, root / "survey.tsv.gz")
            self.assertEqual(summary["written"], 1)
            self.assertEqual(summary["skipped_incomplete"], 2)

    def test_csv_zip_rejects_nonfinite_values_and_preserves_existing_output_on_empty_import(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "foundation.zip"
            with ZipFile(archive, "w") as output:
                output.writestr("export/food.csv", "fdc_id,data_type,description\n1,foundation_food,Bad value\n")
                output.writestr("export/food_nutrient.csv", "fdc_id,nutrient_id,amount\n"
                                "1,1008,100\n1,1003,NaN\n1,1004,0\n1,1005,25\n")
            target = root / "existing.tsv.gz"
            target.write_bytes(b"existing")
            with self.assertRaises(ValueError):
                main(archive, target)
            self.assertEqual(target.read_bytes(), b"existing")


if __name__ == "__main__":
    unittest.main()
