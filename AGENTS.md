## 项目规则

### 代码框架

- 使用 Timber 作为日志框架。
- 不要编写示例代码、演示代码或只用于说明的伪实现。
- 代码注释、项目文档、OpenSpec 实际内容使用中文；OpenSpec 固定关键字和文件名可以保留英文。
- 新功能实现前先检查 `openspec/changes/` 中是否已有对应 change；如果没有，先补 OpenSpec 计划再动代码。

### Kotlin 与 Android

- 新遥控 App 使用 Kotlin 和 Jetpack Compose。
- UI 状态、输入状态、协议状态要分层处理，Compose 层不得直接拼装底层协议帧。
- 日志通过 Timber 输出，避免直接使用 `println` 或 Android `Log`。
- 触控摇杆、外部摇杆、按钮输入统一进入输入层，再由控制层决定是否发送机器人命令。

### 协议与安全

- 机器狗控制协议以 `/home/jiang/code/legged_driver/proto/message.proto` 为真源。
- 当前仓库旧版 `proto/message.proto` 不能作为新 App 协议依据，除非已明确同步到 `legged_driver` 当前版本。
- 移动命令必须有零速度保护：摇杆释放、输入超时、断连、页面后台、失去控制权时都要停止连续运动输出并发送零速度。
- 多 App 共用摇杆数据时，不要依赖多个进程绑定同一个 UDP 端口；优先验证不同客户端端口订阅，必要时使用单采集者分发。

### 文件与素材

- `reverse/` 是反编译和抓取临时目录，禁止提交。
- `docs/assets/genisdog/` 中素材仅作为内部参考或临时开发资源，正式发布前需要替换为自有或已授权资产。
- 修改文档时保持中文表述清晰，避免把设计决策散落在聊天记录里。
