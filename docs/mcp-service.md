# 轻食记 MCP 服务

当前入口：**设置 → AI 设置 → MCP 服务**，页面沿用现有 Miuix 设置规范。

## 连接

开启服务后，复制完整连接地址到小爱的 MCP 配置。本机地址为 `http://127.0.0.1:8765/mcp?token=<凭证>`，也支持 `Authorization: Bearer <凭证>`。首次生成随机 256 位凭证，通过同步落盘与原子文件替换持久保存到应用私有的 `noBackupFilesDir`，不进入系统备份、设备迁移或应用导出。正常关闭、进程结束、重新开启和覆盖升级均复用原凭证。旧版凭证只存内存，首次升级本版本后需更新一次地址。

“打开应用时自动启动 MCP”默认关闭。开启后，主界面进入可见状态时读取偏好并启动现有前台服务；需允许应用通知。后台广播或单独创建 Application 不会触发启动。关闭自动启动不直接停止正在运行的服务；关闭服务后再次打开主界面，若自动启动仍开启，会恢复连接。启动限制参考 [Android 前台服务后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)。

关闭服务仅暂停连接，不删除凭证。“重置连接地址”需先关闭服务并确认，重置后旧凭证永久失效，开启后复制新地址更新小爱。清除数据或卸载同样需要重新配置。

仅监听本机。服务运行时显示管理通知，支持设置开关和通知栏停止。“检查连接”验证握手、工具发现和营养查询，不会发起饮食识别。

## 后台运行设置

MCP 页面提供电池优化豁免申请，并通过 `PowerManager.isIgnoringBatteryOptimizations` 展示真实状态，返回页面时刷新。已豁免时可打开系统优化列表管理。仅在用户点击时申请，不在开启服务时自动弹出。

自启动与后台省电策略在小米/Redmi/POCO 上尝试厂商设置入口；页面不存在或禁止外部访问时回退应用详情，其他厂商直接打开应用详情并提示手动配置。厂商设置没有统一的公开状态接口，页面不显示虚假的“已开启”。系统自启动许可与本应用的“打开应用时自动启动 MCP”是不同设置；本应用不通过开机广播自动启动 MCP。

后台权限可减少待机限制，但不能保证进程永不被终止。强行停止或重启后需要重新开启 MCP，或在开启自动启动后打开应用恢复；连接地址保持不变。参考 [Android 电池优化说明](https://developer.android.com/training/monitoring-device-state/doze-standby)、[小米后台自启动](https://www.mi.com/global/support/faq/details/KA-497677/) 与 [小米省电策略](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1628)。厂商 Activity 路径属于兼容性快捷入口，以实际启动结果为准。

2026-10-13 后台设置验证：Debug/Release 构建通过，Lint 0 错误、75 警告（其中新增 `BatteryLife` 提醒涉及电池豁免申请，另一项为依赖更新提示）。Android 15 模拟器验证实际系统授权弹窗、允许后状态刷新、自启动入口回退应用详情；Miuix 页面截图已检查。小米专用入口尚未实机验证。新版 Release 使用原项目签名并通过 `apksigner` 验证。当前电池豁免申请服务于用户主动开启的本机 MCP 持续连接；若发布 Google Play，需核对核心功能是否满足直接豁免申请政策。

## 查询当天营养

`get_today_nutrition` 无参数，返回今天已记录的热量、蛋白质、碳水和脂肪，以及对应的每日目标。结果位于 `result.content[0].text`，为 JSON 字符串。

包括 `schema_version=1`、`date`、`timezone`、`source=qingshiji`、`is_mock=false`、`status`、`calories_kcal`、`protein_g`、`carbohydrates_g`、`fat_g`、`targets`、`missing_fields` 和 `message`。不包含饮水字段。

`targets` 使用相同的四个营养字段及单位，读取每次查询时的当前设置。热量使用 `dailyCalorieTarget`，三大营养素沿用目标设置的有效值：手动设置优先，否则使用已有个人资料推荐值。目标未设置且无推荐值时返回 `null`，并在 `missing_fields` 中标记如 `targets.protein_g`，不将缺失目标解释为零；没有饮食记录时仍返回目标。该字段为兼容性新增，原有摄入字段保持不变。首页图表在目标缺失时使用的展示兜底值不作为已设置目标返回。

沿用现有日期口径，直接汇总已按食用份量换算的记录。无记录返回 `no_records` 和已记录总量 0；读取失败返回工具错误，不用模拟数据或零值兜底。

## 小爱文字录入

新增工具 `record_food_from_text`，将用户的完整饮食描述交给轻食记现有文字录入流程。小爱无需拆分食物、换算份量或填写营养值。

```json
{
  "text": "午饭吃了 200 克米饭和两个鸡蛋",
  "meal_type": "lunch"
}
```

`text` 必填，最多 4000 字符。`meal_type` 可选，接受 `breakfast`、`lunch`、`dinner`、`snack`；不提供时沿用按当前时间选择餐次的规则。只接受这两个字段。

MCP 构造与原有文字录入相同的 `RecognitionRequestType.TEXT` 请求，原文同时进入 `text` 和 `foodName`，交给原有 `RecognitionTaskStore` 与 `RecognitionService`。后续全部复用：文字识别、营养数据库匹配、实时进度通知、结果同步、原有结果页编辑及确认保存。

服务尝试打开原有识别结果页。Android 可能限制后台拉起页面，此时识别仍走原有前台服务与通知，用户点通知的“查看”进入同一结果页。限制依据：[Android 后台 Activity 启动](https://developer.android.com/guide/components/activities/secure-bal)。

实时活动沿用原有 `NotificationCompat.ProgressStyle`、阶段进度、“取消”“查看”和 promoted ongoing 请求。实际实时活动样式取决于设备系统支持及通知设置；无需新增另一套通知实现。

调用返回 `task_id` 和 `recognizing`；相同原文、餐次与日期在当前任务仍运行或等待核对时复用该任务，已完成时返回 `ready_for_review`。这些状态均不表示已保存。文字识别是异步执行，识别失败和重试沿用原有流程。现有任务已保存或丢弃后，再次提交相同文字可以发起新的记录。

不增加独立草稿页、草稿表、远程保存工具或数据库迁移，数据库继续使用版本 3。也不更改现有识别服务、文字识别客户端、结果卡片或保存逻辑。识别使用用户已有的 AI 配置。

## 传输与权限

采用 [MCP Streamable HTTP JSON 响应](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports)。支持握手、通知、ping、工具发现和调用；不提供 SSE。已授权 GET `/mcp` 返回 405，GET `/health` 只报告存活。

校验访问凭证、Host 和 Origin；请求头限制 16 KiB、请求体限制 64 KiB、Socket 读取超时 10 秒、分发超时 5 秒。文字工具只负责启动原有异步任务，识别进度和生命周期由原服务管理。关闭 MCP 只停止接收新请求，已发起的识别继续沿用原有取消和完成机制。

MCP 前台服务使用 specialUse，识别继续使用原有 dataSync 服务及权限。本机访问凭证是应用访问控制，不是 OAuth。服务不记录饮食描述、营养结果或凭证。

## 验证

2026-10-14 v1.5.1 发布验证：版本码 35；Release 构建成功，APK 签名校验通过；219 项单元测试及 Android 15 模拟器上的 `McpLifecycleTest`、`McpTextRecordingFlowTest` 两项设备测试通过；Lint 无错误（79 项警告、11 项提示）。发布包为 `Lightmemo-v1.5.1.apk`。

2026-10-14 固定凭证与自动启动验证：219 项单元测试通过；Lint 0 错误；Debug/Release 构建与 APK 签名核验通过。Android 15 模拟器通过两个设备测试，覆盖自动启动开启/关闭、服务重启保留原地址、重置后旧凭证返回 401、新凭证返回 200，以及原有后台文字识别与实时通知。设置开关和重置确认弹窗已截图检查。另通过真实强行停止进程并重新打开应用，检查凭证保持一致且前台 MCP 服务自动恢复。新版签名包为 `Lightmemo-v1.5.0-mcp-stable.apk`。

### 后台占用优化

MCP 请求线程按需创建，空闲 30 秒后全部回收，保留阻塞等待连接的监听线程；最大并发仍为 4、队列仍为 16。JSON 分发使用当前请求线程执行，数据库等挂起操作沿用各自的 IO 调度，避免请求线程等待另一个 IO 线程解析。没有增加轮询、定时心跳或唤醒锁。

应用级小组件监听改为 Room 表变更信号，不再为刷新通知持续读取、解码并持有全部饮食记录和推荐反馈。设置、预设变化也只传递刷新信号。实际小组件刷新仍先检查实例，没有实例时不读取历史；新增小组件仍由原广播触发刷新。删除重复启动的迁移协程，保留原有一次性迁移。

单元测试增加工作线程回收及恢复、挂起请求关闭取消、小组件对饮食新增/修改/删除和推荐反馈变化的通知回归。205 项测试通过，Lint 0 错误、75 警告。Android 15 模拟器观察到实际 HTTP 请求按需创建工作线程，空闲后仅剩 MCP 监听线程；这不代表整个应用只有一个线程，也未据此宣称固定的内存或耗电降幅。

优化后再次通过 `McpTextRecordingFlowTest`，验证应用后台提交文字后仍走原有识别服务、进度通知和结果页同步。Release 沿用项目签名并通过核验，产物为 `Lightmemo-v1.5.0-mcp-low-idle.apk`。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.click.lightmemo.mcp.McpTextRecordingFlowTest'
```

回归测试覆盖 MCP 查询、鉴权、错误分支，以及原文转交、餐次、重试、页面启动受限及识别服务启动失败。

模拟器端到端测试通过本机 MCP 发起文字录入，使用测试模型响应，验证原有 TEXT 请求、前台进度通知、数据库匹配和 AddFoodViewModel 原有结果页同步。模型测试响应通过测试端拦截器提供，不修改正式识别逻辑，也不调用真实 AI 服务。

新增工具仍需在实际小爱环境确认调用、原文传递和 HyperOS 实时活动展示。

2026-10-13 验证结果：202 项单元测试通过；Lint 无错误（73 项既有警告）；Android 15 模拟器在应用退到后台 12 秒后，通过真实 MCP HTTP 请求启动原有文字识别服务，验证前台进度通知、原文传递、数据库匹配、原结果页状态同步及未自动保存。测试模型响应由拦截器提供。Release 包使用项目原签名并通过 APK 签名验证。
