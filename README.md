# 轻食记 (food-android)

拍照/手动记录食物热量与营养，并统计当日与近 7/30 天摄入。

## 技术

- Kotlin + Jetpack Compose
- 本地 UI 库：`D:\Code\miuix-glass`（`includeBuild` composite）
- Room + DataStore
- OpenAI 兼容 Vision API

## 环境

- JDK 21
- Android SDK 36（`local.properties` 中 `sdk.dir`）
- minSdk 35

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew :app:assembleDebug
```

首次构建会通过 composite 编译 `../miuix-glass` 相关模块，耗时较长。

## 使用前配置

1. 打开 App → 设置
2. 填写 OpenAI 兼容 `Base URL`（如 `https://api.openai.com/v1`）
3. 填写 `API Key` 与模型名（默认 `gpt-4o-mini`）
4. 设置每日热量目标

未配置 API 时仍可手动录入；拍照/相册识别会提示先配置。

## 功能

- 今日：热量进度、三大营养素、按餐次列表、删除
- 记录：拍照识别 / 相册识别 / 手动录入，可编辑后保存
- 统计：近 7/30 天每日热量柱状图与日均
- 设置：API 与目标热量
