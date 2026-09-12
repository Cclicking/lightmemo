# 轻食记 (food-android)

拍照/手动记录食物热量与营养，并统计当日与近 7/30 天摄入。

## 技术

- Kotlin + Jetpack Compose + AGP 9
- UI：`miuix-glass`（本地 `mavenLocal` 版本 `0.9.4-rclocal`，来自 `D:\Code\miuix-glass` 发布）
- DataStore JSON 持久化 + DataStore 设置
- OpenAI 兼容 Vision API

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
4. 设置每日热量目标

未配置 API 时仍可手动录入；拍照/相册识别会提示先配置。

## 功能

- 今日：热量进度、三大营养素、按餐次列表、删除
- 记录：拍照识别（运行时申请相机权限）/ 相册识别 / 手动录入，可编辑名称与营养后保存
- 统计：近 7/30 天每日热量柱状图、日均、达标天数
- 设置：API 与目标热量
- Glass：`GlassNavigationBar` + `glassPanel`；无 shader 时回退半透明/实心表面
