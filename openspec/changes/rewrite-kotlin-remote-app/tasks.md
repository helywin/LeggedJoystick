## 0. 工程升级与旧实现隔离

- [x] 0.1 升级 Gradle、AGP、Kotlin、AndroidX、Compose、Wire 等依赖到当前稳定版。
- [x] 0.2 完成 AGP 9 构建脚本迁移：移除 `org.jetbrains.kotlin.android`、迁移 `kotlinOptions` 和旧 `applicationVariants` API。
- [ ] 0.3 将旧 UI、旧协议、旧虚拟摇杆和厂商相关入口从新主流程隔离。
- [ ] 0.4 确认新主流程是否直接替换 `MainActivity`，并清理旧入口对新分层的影响。

## 1. 协议真源

- [x] 1.1 同步 `/home/jiang/code/legged_driver/proto/message.proto` 到新 App 的协议生成流程。
- [x] 1.2 使用 Wire 生成 Kotlin 类型，并清理旧 proto 的误用入口。
- [x] 1.3 实现 `LeggedDriverMessage` 构造、时间戳、设备类型、设备 ID 和 CRC32。

## 2. 传输与状态

- [ ] 2.1 实现 ZMQ DEALER 客户端、发送队列、接收循环、心跳和连接恢复。
- [ ] 2.2 实现状态订阅：连接状态、AppMode、RobotState、MotionData、FaultData、Odometry。
- [x] 2.3 用 Timber 记录连接、重连、发送失败、CRC 异常和协议解析异常。
- [x] 2.4 连接按钮必须可重复恢复：每次用户主动点击连接前，彻底关闭旧 socket、旧 context、旧线程任务和旧发送队列，失败后再次点击不得依赖重启 App。
- [x] 2.5 `CONNECTING` 或 `CONNECTED` 状态下不得重复创建第二套 ZMQ 资源；需要重新连接时必须先走显式断开和资源释放。

## 3. 输入层

- [x] 3.1 抽象 `RemoteInputSource`、`RemoteInputSnapshot` 和 `MovementIntent`，后续其他遥控器必须适配同一输入边界。
- [x] 3.2 实现 UniRC UDP 外部摇杆输入：死区、归一化、超时归零，不按 UI 速度档做二次倍率缩放。
- [x] 3.3 实现 UniRC 通道帧解析：STX、Data_len、CMD_ID、CRC16、CH1 到 CH16。
- [x] 3.4 实现输入超时、链路自恢复和调试状态显示。
- [ ] 3.5 验证不同 UDP 客户端端口是否可同时订阅 UniRC 通道数据。
- [x] 3.6 验证 UDP 与串口是否可同时接收 UniRC 通道数据；结论是本机 UDP 桥和直接读 `/dev/ttyHS3` 会竞争，不适合作为两个 App 的独立输入。
- [ ] 3.7 验证 UniRC `freq = 0` 是否会影响串口输出；未验证通过前，退出主控页不得默认发送 `freq = 0`。
- [x] 3.8 在 `Standard-10inch_A2` 上验证 `/dev/ttyHS3` 可收到有效 UniRC `CMD_ID = 0x42` 通道帧，并记录到 `docs/remote_input_device_verification.md`。
- [x] 3.9 验证 `Standard-10inch_A2` 本机 `com.siyi.udpservice` 监听 `127.0.0.1:19856`，新 App 可绑定并请求打开串口桥，且可收到有效 `0x42` 通道帧。

## 4. 主屏 UI

- [x] 4.1 按 `docs/genisdog_main_screen_layout.md` 实现全屏主控页骨架。
- [x] 4.2 实现顶部运动模式 overlay，支持普通、原地、楼梯模式。
- [x] 4.3 实现左侧低速、中速、高速三段垂直速度选择器。
- [x] 4.4 实现底部动作组展开/收缩，开关按钮不发送机器人命令。
- [x] 4.5 实现电量和机器状态 overlay，绑定 `RobotStateMessage`。
- [ ] 4.6 正式发布前替换 GenisDog 抓取素材；如使用 imagegen，最终资源必须是透明背景 PNG。
- [x] 4.7 保留 `Standard-10inch_A2` 横屏右侧系统虚拟导航栏空间，主控页和视频页不得进入完全沉浸式全屏。

## 5. 控制命令

- [x] 5.1 实现接管、释放、手动/自动模式和运动模式命令。
- [x] 5.2 实现连续 `COMMAND_CODE_MOVE` 发送和零速度保护。
- [x] 5.3 实现站立、卧倒、匍匐、锁定、爬高墙、扭一扭、过窄墙等动作命令。
- [ ] 5.4 实现前灯、后灯、自动补光、头部控制和高低姿态。

## 6. 设置与联调

- [ ] 6.1 实现机器狗地址、ZMQ 端口、视频地址设置，并在工程调试页实现摇杆死区、通道映射和轴反向。
- [ ] 6.2 实现调试信息面板，显示原始通道值、归一化轴值、输入来源和最后发送命令。
- [ ] 6.3 使用 mock 或真实 `legged_driver` 环境完成联调。
- [ ] 6.4 验证后台、断网、杀进程、输入超时、控制权丢失和停止输出保护场景。

## 7. 提交验证

- [ ] 7.1 每次功能提交前通过 `openspec validate rewrite-kotlin-remote-app --strict`。
- [ ] 7.2 每次功能提交前通过 Gradle 构建和核心单元测试。
- [ ] 7.3 每次 UI 功能提交前通过工程 mock 模式主屏截图检查。
- [ ] 7.4 涉及协议封包、CRC、UniRC 输入解析或轴映射时，额外通过协议封包/CRC 测试和 UniRC 帧解析测试。

## 8. 当前实施切片

- [x] 8.1 协议真源切片：同步 `legged_driver` 当前 `message.proto`，重写协议封包工具，并用单元测试覆盖 CRC32、订阅和命令封包。
- [x] 8.2 传输恢复切片：重写 ZMQ 连接生命周期，保证连接按钮失败后再次点击能重建完整连接资源，不需要重启 App。
- [x] 8.3 旧入口隔离切片：将旧虚拟摇杆、Android 游戏手柄输入和旧模式入口从新主流程剥离，避免误发旧协议语义。
- [ ] 8.4 UniRC 输入切片：实现 UDP 通道订阅、帧解析、死区、轴映射、超时归零，并继续验证本机 UDP 桥返回有效通道帧和串口/UDP 并行边界。
- [x] 8.5 主屏 UI 切片：按 GenisDog 主屏截图实现横屏主控页、运动模式 overlay、速度三挡、电量 overlay 和底部动作组。
- [x] 8.6 控制权切片：实现接管/释放命令、控制权 ACK/结果处理、控制权丢失停止输出，以及运动/动作命令控制权闸门。
