# 轻食记 (food-android)

拍照/手动记录食物热量与营养，并统计当日与近 7/30 天摄入。

## 技术

- Kotlin + Jetpack Compose + AGP 9
- UI：`miuix-glass`（本地 `mavenLocal` 版本 `0.9.4-rclocal`，来自 `D:\Code\miuix-glass` 发布）
- DataStore JSON 持久化 + DataStore 设置
- OpenAI 兼容 Vision API（只负责菜品识别、拆解、估重与二次审核）
- USDA FoodData Central SR Legacy 离线营养库（7,793 条；负责确定性营养计算）
- 中国食物成分表第 6 版离线回退库（1,635 条完整宏量营养记录）

## 环境

- JDK 21
- Android SDK 37（`local.properties` 中 `sdk.dir`）
- minSdk 35 / compileSdk 37
- 本机 `~/.m2` 中需有 `top.yukonga.miuix.kmp:*-android:0.9.4-rclocal`

### 刷新本地 miuix 包（改了 miuix 源码时）

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd D:\Code\miuix-glass
.\gradlew `
  :miuix-core:publishAndroidPublicationToMavenLocal `
  :miuix-shader:publishAndroidPublicationToMavenLocal `
  :miuix-squircle:publishAndroidPublicationToMavenLocal `
  :miuix-blur:publishAndroidPublicationToMavenLocal `
  :miuix-glass:publishAndroidPublicationToMavenLocal `
  :miuix-ui:publishAndroidPublicationToMavenLocal `
  :miuix-preference:publishAndroidPublicationToMavenLocal `
  :miuix-icons:publishAndroidPublicationToMavenLocal `
  :miuix-nav:publishAndroidPublicationToMavenLocal `
  -Prc=local
```

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew :app:assembleDebug
.\gradlew :app:testDebugUnitTest
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 使用前配置

1. 打开 App → 设置
2. 填写 OpenAI 兼容 `Base URL`（如 `https://api.openai.com/v1`）
3. 填写 `API Key` 与模型名（默认 `gpt-4o-mini`）
4. 可选填写 USDA FoodData Central API Key；查询顺序为 USDA 离线库 → USDA API → 中国食物成分离线库
5. 设置每日热量目标

未配置 API 时仍可手动录入；拍照/相册识别会提示先配置。

## 功能

- 今日：热量进度、三大营养素、按餐次列表、删除
- 记录：图片质量检查 → 完整菜品识别 → 组成拆解 → 二次视觉审核 → USDA 标准食物映射 → 按重量计算并汇总
- 确认：按菜品分层展示组成、估计区间、置信度和数据出处；修改任一组成的克数会即时重算菜品与整餐
- 统计：近 7/30 天每日热量柱状图、日均、达标天数
- 设置：API 与目标热量
- Glass：带图标、文字和选中胶囊的 `GlassNavigationBar`，以及 `glassPanel` 蓝色添加按钮；导航与按钮同排自适应宽度
- 卡片：使用本地 Miuix 的 `Card`，内容卡片统一 16dp 内边距，设置行使用组件自带留白
- 背景捕获：`layerBackdrop` 包含页面底色与滚动内容，为玻璃导航和顶部栏提供完整采样

## 更新离线营养库

从 USDA FoodData Central 下载 SR Legacy CSV 后运行：

```powershell
python tools/generate_fdc_asset.py <CSV解压目录> app/src/main/assets/fdc_sr_legacy_macros.tsv.gz
python tools/generate_china_food_asset.py <food_composition_full.csv> app/src/main/assets/china_food_composition.tsv.gz
```

运行时库只保留来源 ID、名称和每 100g 的热量/蛋白质/碳水/脂肪。数据来源与许可/使用限制见 `docs/USDA-FoodData-Central-NOTICE.md`。
