# USDA FoodData Central 数据说明

本项目内置的 `fdc_sr_legacy_macros.tsv.gz` 由 FoodData Central 的 SR Legacy 2018-04 CSV 生成，
仅保留应用计算需要的 FDC ID、食物描述以及每 100g 的能量、蛋白质、碳水化合物和脂肪。

- 来源：U.S. Department of Agriculture, Agricultural Research Service, FoodData Central
- 下载页：https://fdc.nal.usda.gov/download-datasets/
- 数据版本：SR Legacy, 2018-04（最终版）
- 许可：CC0 1.0 / public domain

生成方式见 `tools/generate_fdc_asset.py`。视觉模型不生成营养值；所有展示值都由数据库每 100g
数据乘以可食用重量后计算，并由程序逐级汇总。

## 中国食物成分回退库

`china_food_composition.tsv.gz` 由
[`Sanotsu/china-food-composition-data`](https://github.com/Sanotsu/china-food-composition-data)
的 `json_data_v3_20260825_qwen38max_kimi_k3_fixed_en/food_composition_full.csv` 生成：

- 上游提交：`d15675c27582748307023b7ee7aca2a63fc52756`
- 原始条目：1,677；保留四项宏量营养完整的 1,635 条（`Tr` 按 0、带 `*` 脚注数值按其数值解析）
- 字段：食物编码、中英文名称、每 100g 热量/蛋白质/碳水/脂肪
- 查询优先级：USDA 离线库 → 可选 USDA API → 本回退库

上游仓库没有提供开源许可证，并在 README 中声明版权归原作者、脚本仅用于个人学习研究。
因此该数据不能视为 CC0，也不应在未取得相应授权时用于公开分发或商业发布。
