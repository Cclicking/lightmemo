# LightMemo · 轻食记

拍照识别或手动记录一餐的热量与营养，准确拆分基础食物，数据库匹配热量与营养。

**minSdk** 35

---

## 功能展示

### 今日

- **热量总览**：当日摄入大数字 + 进度条，对照每日目标一眼可见
- **三大营养素环**：蛋白质 / 碳水 / 脂肪各自进度与目标
- **按餐次组织**：早餐、午餐、晚餐、加餐；有记录才出现标题，空态有引导卡片


### 记录一餐

- **拍照 / 相册**：相机权限申请，Photo Picker 选图
- **识别流水线**：图片质量检查 → 菜品识别 → 组成拆解 → 二次视觉审核 → 映射标准食物 → 按重量计算
- **确认页可改**：按菜品展示组成、估计重量区间、置信度与数据出处；改任一克数即时重算整餐
- **手动录入兜底**：未配置 API 也能直接填名称与营养

### 统计

- **共用日期组件**：与今日页同一套日历（周 / 月、跟手滑动、标题样式一致）
- **日均与达标**：记录日数、日均热量、未超目标 / 超标天数
- **饮食结构**：各餐次热量占比环图
- **每日柱状图**：近一段时间逐日摄入，点选查看单日详情
- **宏量摄入**：平均蛋白 / 碳水 / 脂肪对照目标

### 我的 · 设置

- 每日热量与宏量目标
- OpenAI 兼容 Vision API（Base URL / Key / 模型）
- USDA FoodData Central API Key（可选）
- 数据备份与恢复、食物库管理、外观（玻璃效果等）

---

## 界面

整体是 HyperOS 风格的液态玻璃，内容铺在可采样的背景层上，导航与按钮实时折射下层画面。

| 区域 | 表现 |
|------|------|
| 底栏 | 胶囊玻璃 Tab，拖动时有阻尼拉伸与速度拉长；中央 **Liquid Add** 圆形按钮可按压缩放、拖拽跟手形变 |
| 顶栏 | 可折叠大标题 + 渐进模糊；右侧液态图标按钮（管理卡片、切换月视图） |
| 卡片 | Miuix `Card`，内容区统一 16dp 边距；跨页滑动时页宽 = 卡片宽 + 间隙 |
| 弹层 | 添加食物使用毛玻璃 Bottom Sheet；详情 / 编辑为圆角浮层 |
| 背景 | `layerBackdrop` 捕获页面底色与滚动内容，供玻璃导航、按钮、卡片采样 |

无 Runtime Shader 时降级为半透明表面，保证功能可用。

---

## 技术

### 栈

- **Kotlin** + **Jetpack Compose**（BOM 2025.10）+ **AGP 9** / JDK 21
- **UI**：本地 `miuix-glass`（`top.yukonga.miuix.kmp:*-android:0.9.4-rclocal`）
- **持久化**：DataStore JSON 日志 + DataStore 设置
- **网络**：OkHttp + kotlinx.serialization
- **图像**：Coil 3
- **识别**：OpenAI 兼容 `chat/completions` Vision（菜品拆解、估重、审核）
- **营养库**：
  - USDA FoodData Central SR Legacy 离线库（约 7,793 条，确定性计算）
  - 中国食物成分表第 6 版离线回退（约 1,635 条完整宏量）
  - 可选 USDA 在线 API；查询顺序：离线 USDA → API → 中国库

### 模块结构

```
app/
├── ui/          页面、液态玻璃组件、特效（highlight / edge light / overscroll）
├── data/        FoodLog 仓库、设置、预设食物、校验
├── domain/      Nutrition / FoodLog / MealType
├── network/     Vision 客户端、USDA FDC 客户端
└── viewmodel/   Today / Stats / Add / Settings / Backup
```

### 构建

```powershell
# 需 JDK 21 + Android SDK 37，local.properties 配置 sdk.dir
.\gradlew :app:assembleDebug
.\gradlew :app:testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

依赖本机 `~/.m2` 中的 miuix 本地包。若改过 miuix 源码，在 `miuix-glass` 工程执行各模块 `publishAndroidPublicationToMavenLocal`（`-Prc=local`）。

### 使用前配置

1. 打开 App → 设置
2. 填写 OpenAI 兼容 Base URL（如 `https://api.openai.com/v1`）、API Key、模型名
3. 可选：USDA FDC API Key
4. 设置每日热量目标

未配置 API 时仍可手动录入；拍照识别会提示先完成配置。

### 更新离线营养库

```powershell
python tools/generate_fdc_asset.py <CSV解压目录> app/src/main/assets/fdc_sr_legacy_macros.tsv.gz
python tools/generate_china_food_asset.py <food_composition_full.csv> app/src/main/assets/china_food_composition.tsv.gz
```

数据来源与许可见 [`docs/USDA-FoodData-Central-NOTICE.md`](docs/USDA-FoodData-Central-NOTICE.md)。
