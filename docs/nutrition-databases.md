# 离线营养数据库

应用使用每 100g 可食部的热量、蛋白质、碳水化合物和脂肪，乘以组成重量计算营养。
本次扩充未改变营养模型；原始数据中的微量营养素、份量重量、配方明细未打入应用。

## 已内置的数据

| 数据源 | 快照 | 保留条目 | 资产 |
| --- | --- | ---: | --- |
| USDA Foundation | 2026-04 | 367 | `fdc_foundation_macros.tsv.gz` |
| USDA SR Legacy | 2018-04（最终版） | 7,793 | `fdc_sr_legacy_macros.tsv.gz` |
| USDA FNDDS | 2021–2023，2024-10 发布 | 5,431 | `fdc_fndds_macros.tsv.gz` |
| 中国食物成分表 | 第6版，上游2026-09英文增强版 | 1,635 | `china_food_composition.tsv.gz` |

USDA 合计 13,591 条；包括中国库合计 15,226 条。不同数据库的条目可能描述相同食物，
该总数不是去除食物语义重复后的品种数。来源及版本保存在每条 `NutritionReference.dataType` 中，
ID 保留原始 FDC ID；旧记录中的引用不做替换。

## 官方下载与校验和

核查日期：2026-10-16。[官方数据下载页](https://fdc.nal.usda.gov/download-datasets/)。

Foundation 使用 [2026-04 CSV ZIP](https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_csv_2026-04-30.zip)。

- 下载包 SHA-256：`70457ee9d9342f43bda2010318c85f04210c689fdeb9cd2da4c513b0e8dbc655`
- 原包 `food.csv` 包含 469 条 `foundation_food` 主食物记录，另有大量实验样品等内部记录；内部记录不导入。
- 保留四项营养完整的 367 条，跳过 102 条不完整记录。
- 本次检查同版 JSON 包只包含 363 个非空对象及 32 个空记录，故选用覆盖更多有效主记录的 CSV。
- 热量取自 2048 的 304 条、2047 的 32 条、1008 的 31 条。
- 生成资产 SHA-256：`4bf0f328ef3faaa816ffe9b34fb0edba03b3ed6f76369f38823572878fb51fb8`

FNDDS 使用 [2021–2023 JSON ZIP](https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip)。

- 下载包 SHA-256：`dfb06ae7ddc397ccd570b91c14b75438ab2ba39f64f22d321f61d4a52a77f3eb`
- 原包 5,432 条；`Milk, human`（FDC ID 2705383）没有营养值，跳过；保留 5,431 条。
- 热量均取自 1008。
- 生成资产 SHA-256：`73f177eaf42ab7af69ce5c823fe1c7cca96f650305953b383d4b39f28645e413`

上述哈希标识本次实际获取的文件，官方若重新发布同名文件应重新核对。
USDA 数据许可说明见 [第三方来源说明](THIRD-PARTY-NOTICES.md)。

## 导入规则与复现

`tools/generate_fdc_asset.py` 接受解压后的 CSV 目录、官方 CSV/JSON ZIP 或 JSON 文件。
按 FDC ID 排序，gzip 时间戳固定为 0，同一输入可生成相同资产。

- 仅导入主食物记录，排除采样、实验和市场采购记录。
- 热量依次选择 2048（Atwater Specific）、2047（Atwater General）、1008（Energy），均为 kcal。
- 宏量字段使用 1003（蛋白质）、1005（碳水）、1004（脂肪），均为 g。
- 四项必须完整且为有限非负数；缺失不补零，不用 kJ 代替 kcal，也不从宏量反推热量。
- 合法的零值保留；FDC ID 重复或导入结果为空时失败。

在项目根目录运行，下载包放在忽略版本控制的 `build/nutrition-import/`：

```powershell
python tools/generate_fdc_asset.py build/nutrition-import/foundation-2026-04-csv.zip app/src/main/assets/fdc_foundation_macros.tsv.gz
python tools/generate_fdc_asset.py build/nutrition-import/fndds-2021-2023.zip app/src/main/assets/fdc_fndds_macros.tsv.gz
python -m unittest discover -s tools -p test_generate_fdc_asset.py
```

## 运行时匹配

三套 USDA 库共同参与排序，相关程度相同时优先 Foundation、SR Legacy、FNDDS。
所有来源都要通过食物身份、生熟和做法检查才能自动采用；候选展示可保留较宽泛的结果供用户确认。
FNDDS 中未说明具体食物/做法的 NFS、NS 条目，以及检索词未明确指定的配方、加油或附加食材，
不直接替代普通食材。整菜作为一个组成采用时，不应再次计入已包含的油或配料。

三套离线库在诊断统计中均归为 USDA 离线命中；数据库浏览支持各自独立筛选。

常用检索词支持全角 `［别名］` 和多个顿号分隔的别名。“块、片、丝、丁”等形态词仅在查询的
剩余部分为已知食物时去掉，不更改数据库条目的别名，避免把牛肉部位“黄瓜条”当作黄瓜。
青椒同时召回甜椒与青尖椒，优先采用甜椒；青菜按原表中的“小白菜［青菜］”别名匹配。
橘子／桔子统一查找橘类水果；果汁、糖渍或罐头不能自动替代普通水果。

英文匹配区分 cucumber 与 sea cucumber、broccoli 与 broccoli raab/rabe，以及 chicken 与 soy/mock chicken。
候选排序优先普通食材，腌制等加工食品仍可在相关查询中作为候选选择。
未指定部位和具体做法的熟鸡肉可以匹配同类熟制鸡肉；用户明确指定的部位、做法和生熟要求仍保留。
