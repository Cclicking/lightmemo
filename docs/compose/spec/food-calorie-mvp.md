---
feature: food-calorie-mvp
status: delivered
updated: 2026-09-12
branch: feature/food-calorie-mvp
commits: 2aac7c6..7d5accc
---

# 食物热量识别与统计 MVP

## Report

**What was built** — 「轻食记」Android 应用：OpenAI 兼容 Vision 拍照/相册识别食物热量与营养，手动录入兜底；当日热量进度与三大营养素、按餐次列表；近 7/30 天柱状图与达标天数；设置页配置 API 与目标热量。UI 基于本地 `miuix-glass`（HyperOS 玻璃拟态），`GlassNavigationBar` + `glassPanel`，无 shader 时回退半透明表面。持久化为 DataStore JSON（替代 Room，因 AGP 9/KSP 摩擦）。

**Verification** — `./gradlew :app:assembleDebug` PASS（`app/build/outputs/apk/debug/app-debug.apk` ~41MB）；`./gradlew :app:testDebugUnitTest` PASS（2 tests）。独立 review 发现 critical：相机权限未请求；已修复（运行时 `RequestPermission` + 拒绝提示）并重跑构建/测试 PASS。其余 review 项（可编辑宏量、达标天数、识别遮罩、餐次选中态、README/spec 对齐）已处理。

**Journey log** —
1. 空仓库起步；环境无 JDK/SDK，安装 Temurin 21 + Android cmdline-tools/platform 35–37。
2. 嵌套 worktree 被环境拦截 → 改在主 checkout 功能分支 `feature/food-calorie-mvp`。
3. `includeBuild("../miuix-glass")` + 坐标替换可行；需 compileSdk 37 与 Kotlin 2.4.10 对齐 miuix。
4. Room/KSP 与 AGP 9 摩擦 → 改 DataStore JSON 持久化。
5. Review critical：声明 CAMERA 但无运行时请求 → 已修；Photo Picker 不需要 READ_MEDIA_IMAGES，已移除。

## [S1] Problem

用户需要快速记录饮食：拍照或手动输入识别食物，得到热量与三大营养素，并在当日与近 7/30 天查看摄入统计，以便控制热量与营养结构。现有 `food-diary.html` 仅有静态原型，缺少真实识别与本机持久化。

## [S2] Design

### 产品决策（已对齐）

| 轴 | 决策 |
|---|---|
| 识别 | 云端 OpenAI 兼容 Vision API（真实 API，无 Mock 兜底） |
| 输入 | 相机拍照 + 相册选图 + 手动录入 |
| 范围 | 核心 MVP + 周期统计（当日/7天/30天、目标热量、餐次） |
| UI | 本地 `D:\Code\miuix-glass` 的 HyperOS 玻璃拟态组件 |
| minSdk | 35（glass 需要 API 33；用户要求“最新”） |
| 依赖策略 | `includeBuild("../miuix-glass")` composite + 坐标替换 |
| 持久化 | DataStore JSON 日志 + DataStore 设置（非 Room） |

### 架构

```
app (pure Android, single module)
├── ui/          MiuixTheme + Glass 导航与页面
├── data/        DataStore JSON 日志 + DataStore 设置
├── domain/      FoodLog / Nutrition / MealType
├── network/     OpenAI chat.completions Vision 客户端
└── viewmodel/   Today / Stats / Add / Settings
```

- 导航：底部 `GlassNavigationBar` 三 Tab（今日/统计/设置）；添加为全屏路由；无 shader 时 `FloatingNavigationBar`。
- 主题：根 `ThemeController` + `MiuixTheme`；内容置于 `layerBackdrop` 上，玻璃条/卡片消费 backdrop。
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
- 网络/解析失败 → 中文错误文案；识别成功后进入编辑确认页，可改名称/克数/热量/宏量再保存。
- 手动录入不依赖网络。

### 统计契约

- 当日：总 kcal + 目标进度 + 蛋白/碳水/脂肪合计；按餐次分组列表。
- 统计 Tab：切换 7 天 / 30 天；柱状图显示每日 kcal；日均、有记录天数、达标天数（≤目标）、峰值。
- 目标热量默认 1800 kcal，可在设置修改。

### UI / Glass

- 背景：全屏柔和 HyperOS 渐变；内容 `layerBackdrop`。
- `GlassNavigationBar` / `glassPanel` 卡片；Add 顶栏为普通 Row（非 GlassTopAppBar）。
- 无 shader 设备：`isRuntimeShaderSupported()==false` 时回退半透明/实心表面。

### 错误与权限

- 相机：运行时请求 `CAMERA`；拒绝后提示并建议相册。相册使用 Photo Picker（无需 `READ_MEDIA_IMAGES`）。
- API 错误：HTTP 非 2xx、JSON 不完整映射为用户可读中文。
- 识别中全屏遮罩拦截误触。

## [S3] Out of Scope

- 用户账号 / 云同步 / 多设备
- 条形码扫描、食谱库、AI 对话式营养师
- 血糖/体重/运动消耗
- 多语言、无障碍深度调优（后续）
- Room/复杂查询与完整 instrumentation 测试

## Tasks

- [x] T1: 工程骨架（Gradle、miuix-glass composite、Application/MainActivity、主题） — acceptance: `assembleDebug` 配置阶段通过，空 Scaffold 可编译 (covers: S2)
- [x] T2: 领域模型 + DataStore JSON 持久化 + 设置 — acceptance: 插入/删除/按日/区间聚合可运行 (covers: S2; depends: T1)
- [x] T3: OpenAI Vision 客户端 + 识别解析 — acceptance: 样例 JSON 解析测试通过；缺配置时抛出明确错误 (covers: S2; depends: T1)
- [x] T4: 今日页（进度、宏量、餐次列表、删除） — acceptance: 有数据时展示汇总；空态有引导 (covers: S2; depends: T2)
- [x] T5: 添加流（拍照/相册/手动 → 确认编辑 → 保存） — acceptance: 未配置 API 时识别不可用并提示；相机权限运行时申请；可编辑营养后保存 (covers: S2; depends: T2; T3)
- [x] T6: 统计页（7/30 天柱状图与汇总） — acceptance: 切换区间后图表与均值/达标天数更新 (covers: S2; depends: T2)
- [x] T7: 设置页（baseURL/Key/模型/目标热量） — acceptance: 保存后立即影响识别客户端与进度目标 (covers: S2; depends: T2; T3)
- [x] T8: Glass 视觉整合与降级 — acceptance: 导航/卡片使用 glass API；无 shader 时可见回退 (covers: S2; depends: T1)
- [x] T9: 构建验证 `assembleDebug` + Review — acceptance: 本地成功产出 APK；critical 已修并复测 (covers: S2; depends: T1–T8)
