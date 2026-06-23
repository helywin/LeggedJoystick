## 项目规则

### 代码框架

- 使用 Timber 作为日志框架。
- 不要编写示例代码、演示代码或只用于说明的伪实现。
- 代码注释、项目文档、OpenSpec 实际内容使用中文；OpenSpec 固定关键字和文件名可以保留英文。
- 新功能实现前先检查 `openspec/changes/` 中是否已有对应 change；如果没有，先补 OpenSpec 计划再动代码。

### Kotlin 与 Android

- 新遥控 App 使用 Kotlin 和 Jetpack Compose。
- 构建栈优先使用当前稳定版；升级 AGP、Gradle、Kotlin、AndroidX 或 Compose 时只采用稳定版本，不使用 alpha、beta、RC、EAP 或 nightly。
- AGP 9 以后不要再应用 `org.jetbrains.kotlin.android` 插件，使用 AGP 内置 Kotlin 支持和 Kotlin `compilerOptions`。
- UI 状态、输入状态、协议状态要分层处理，Compose 层不得直接拼装底层协议帧。
- 日志通过 Timber 输出，避免直接使用 `println` 或 Android `Log`。
- 外部摇杆和按钮输入统一进入输入层，再由控制层决定是否发送机器人命令。

### 协议与安全

- 机器狗控制协议以 `/home/jiang/code/legged_driver/proto/message.proto` 为真源。
- 当前仓库旧版 `proto/message.proto` 不能作为新 App 协议依据，除非已明确同步到 `legged_driver` 当前版本。
- 移动命令必须有零速度保护：摇杆释放、输入超时、断连、页面后台、失去控制权时都要停止连续运动输出，并发送零速度或停止速度输出让 `driver` 侧超时保护接管。
- 内部移动意图按操作者直觉表达：前进、右平移、右转为正；发送 `legged_driver` 时按 SDK 语义转换，`MoveCommandParams.left_right` 正数是左平移，`yaw` 正数是左旋转。
- UI 低/中/高速只通过 `COMMAND_CODE_SET_SPEED_LEVEL` 生效，输入层不得再按速度档对 `COMMAND_CODE_MOVE` 做二次倍率缩放。
- 多 App 共用摇杆数据时，不要依赖多个进程绑定同一个 UDP 端口；优先验证不同客户端端口订阅，必要时使用单采集者分发。

### 验证要求

- 第一版功能提交前至少通过 OpenSpec 校验、Gradle 构建和核心单元测试。
- UI 功能提交前要通过工程 mock 模式主屏截图检查。
- 涉及协议封包、CRC、UniRC 输入解析或轴映射时，额外通过协议封包/CRC 测试和 UniRC 帧解析测试。

### 文件与素材

- `reverse/` 是反编译和抓取临时目录，禁止提交。
- `docs/assets/genisdog/` 中素材仅作为内部参考或临时开发资源，正式发布前需要替换为自有或已授权资产。
- 正式发布素材可以用 imagegen 生成；项目内使用的位图图标必须是经工具处理后的透明背景 PNG，不能直接使用带纯色背景的生成结果。
- 修改文档时保持中文表述清晰，避免把设计决策散落在聊天记录里。
