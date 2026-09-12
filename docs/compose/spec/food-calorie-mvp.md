---
feature: food-calorie-mvp
status: designed
updated: 2026-09-12
branch: feature/food-calorie-mvp
commits: # filled at delivery
---

# 食物热量识别与统计 MVP

## Report

## [S1] Problem

用户需要快速记录饮食：拍照或手动输入识别食物，得到热量与三大营养素，并在当日与近 7/30 天查看摄入统计，以便控制热量与营养结构。现有 `food-diary.html` 仅有静态原型，缺少真实识别与本机持久化。

## [S2] Design

### 产品决策（已对齐）

| 轴 | 决策 |
|---|---|
| 识别 | 云端 OpenAI 兼容 Vision API（真实 API，无 Mock 兜底） |
| 输入 | 相机拍照 + 相册选图 + 手动录入/搜索 |
| 范围 | 核心 MVP + 周期统计（当日/7天/30天、目标热量、餐次） |
| UI | 本地 `D:\Code\miuix-glass` 的 HyperOS 玻璃拟态组件 |
| minSdk | 35（glass 需要 API 33；用户要求“最新”） |
| 依赖策略 | `includeBuild("../miuix-glass")` composite；必要时 `publishToMavenLocal` |

### 架构

```
app (pure Android, single module)
├── ui/          MiuixTheme + Glass 导航与页面
├── data/        Room 实体/DAO + DataStore 设置
├── domain/      FoodLog / Nutrition / MealType
├── network/     OpenAI chat.completions Vision 客户端
└── viewmodel/   Today / Stats / Add / Settings
```

- 导航：`miuix-nav` 扁平栈，底部 `GlassNavigationBar` 三 Tab：今日 / 统计 / 设置；添加为全屏路由。
- 主题：根 `ThemeController` + `MiuixTheme`；页面内容置于 `LayerBackdrop` 上，玻璃条/卡片消费 backdrop。
- 持久化：Room（`food_log` 表）；设置用 DataStore Preferences。
- 网络：OkHttp + kotlinx.serialization；`POST {base}/chat/completions`，图像 base64 `image_url`。

### 数据模型

```kotlin
data class Nutrition(
  val caloriesKcal: Double,
  val proteinG: Double,
  val carbsG: Double,
  val fatG: Double,
)

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

data class FoodLog(
  val id: Long,
  val name: String,
  val mealType: MealType,
  val grams: Double,
  val nutrition: Nutrition, // 整份摄入量
  val imageUri: String?,
  val dateEpochDay: Long,
  val createdAtMillis: Long,
)
```

### 识别契约

Request（OpenAI 兼容）：

- `model`: 设置中的模型名（默认 `gpt-4o-mini`）
- `messages[0]`: system，要求只输出 JSON
- `messages[1]`: user，含文本指令 + `image_url`（`data:image/*;base64,...`）

Response JSON（模型侧，解析后映射）：

```json
{
  "items": [
    {
      "name": "米饭",
      "grams": 150,
      "caloriesKcal": 174,
      "proteinG": 3.9,
      "carbsG": 38.9,
      "fatG": 0.5
    }
  ]
}
```

- 配置缺失（baseURL 或 API Key 为空）→ 设置页提示，识别按钮禁用并引导填写。
- 网络/解析失败 → 可重试错误条；识别成功后进入编辑确认页，可改名称/克数/营养再保存。
- 手动录入不依赖网络。

### 统计契约

- 当日：总 kcal + 目标进度 + 蛋白/碳水/脂肪合计；按餐次分组列表。
- 统计 Tab：切换 7 天 / 30 天；柱状图显示每日 kcal；汇总均值与达标天数。
- 目标热量默认 1800 kcal，可在设置修改。

### UI / Glass

- 背景：全屏柔和 HyperOS 渐变 + 可选壁纸位图；内容 `layerBackdrop`。
- `GlassTopAppBar` / `GlassNavigationBar` / `GlassPanel` 卡片。
- 无 shader 设备：`isRuntimeShaderSupported()==false` 时回退半透明/实心表面（glass API 的 fallback）。

### 错误与权限

- 相机/相册：`READ_MEDIA_IMAGES`（33+）/ 相机权限；拒绝后隐藏对应入口并提示。
- API 错误：HTTP 非 2xx、JSON 不完整、图片过大（>8MB base64）均映射为用户可读中文。

## [S3] Out of Scope

- 用户账号 / 云同步 / 多设备
- 条形码扫描、食谱库、AI 对话式营养师
- 血糖/体重/运动消耗
- 多语言、无障碍深度调优（后续）
- 单元测试覆盖网络层 Mock 全链路（MVP 以编译与手工路径为准；Room/解析提供基础 JVM 测试）

## Tasks

- [ ] T1: 工程骨架（Gradle、miuix-glass composite、Application/MainActivity、主题） — acceptance: `assembleDebug` 配置阶段通过，空 Scaffold 可编译 (covers: S2)
- [ ] T2: 领域模型 + Room + DataStore 设置 — acceptance: 插入/查询/按日聚合的 DAO 测试或可运行查询 (covers: S2; depends: T1)
- [ ] T3: OpenAI Vision 客户端 + 识别解析 — acceptance: 给定样例 JSON 字符串可解析为 items；缺配置时抛出明确错误 (covers: S2; depends: T1)
- [ ] T4: 今日页（进度、宏量、餐次列表、删除） — acceptance: 有数据时展示汇总；空态有引导 (covers: S2; depends: T2)
- [ ] T5: 添加流（拍照/相册/手动 → 确认编辑 → 保存） — acceptance: 未配置 API 时拍照识别不可用并提示；手动可保存 (covers: S2; depends: T2; T3)
- [ ] T6: 统计页（7/30 天柱状图与汇总） — acceptance: 切换区间后图表与均值更新 (covers: S2; depends: T2)
- [ ] T7: 设置页（baseURL/Key/模型/目标热量） — acceptance: 保存后立即影响识别客户端与进度目标 (covers: S2; depends: T2; T3)
- [ ] T8: Glass 视觉整合与降级 — acceptance: 导航/顶栏/卡片使用 glass API；API<33 或无 shader 时可见回退 (covers: S2; depends: T1)
- [ ] T9: 构建验证 `assembleDebug` + Review — acceptance: 本地成功产出 APK 或明确环境缺失；review 无 critical (covers: S2; depends: T1–T8)
