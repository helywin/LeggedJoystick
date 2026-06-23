## 0. 工程升级与旧实现隔离

- [x] 0.1 升级 Gradle、AGP、Kotlin、AndroidX、Compose、Wire 等依赖到当前稳定版。
- [x] 0.2 完成 AGP 9 构建脚本迁移：移除 `org.jetbrains.kotlin.android`、迁移 `kotlinOptions` 和旧 `applicationVariants` API。
- [ ] 0.3 将旧 UI、旧协议、旧虚拟摇杆和厂商相关入口从新主流程隔离。
- [ ] 0.4 确认新主流程是否直接替换 `MainActivity`，并清理旧入口对新分层的影响。

## 1. 协议真源

- [ ] 1.1 同步 `/home/jiang/code/legged_driver/proto/message.proto` 到新 App 的协议生成流程。
- [ ] 1.2 使用 Wire 生成 Kotlin 类型，并清理旧 proto 的误用入口。
- [ ] 1.3 实现 `LeggedDriverMessage` 构造、时间戳、设备类型、设备 ID 和 CRC32。

## 2. 传输与状态

- [ ] 2.1 实现 ZMQ DEALER 客户端、发送队列、接收循环、心跳和重连。
- [ ] 2.2 实现状态订阅：连接状态、AppMode、RobotState、MotionData、FaultData、Odometry。
- [ ] 2.3 用 Timber 记录连接、重连、发送失败、CRC 异常和协议解析异常。

## 3. 输入层

- [ ] 3.1 实现 UniRC UDP 外部摇杆输入：死区、归一化、协议符号转换、释放归零，不按 UI 速度档做二次倍率缩放。
- [ ] 3.2 实现 UniRC 通道帧解析：STX、Data_len、CMD_ID、CRC16、CH1 到 CH16。
- [ ] 3.3 实现输入超时、链路自恢复和调试状态显示。
- [ ] 3.4 验证不同 UDP 客户端端口是否可同时订阅 UniRC 通道数据。
- [ ] 3.5 验证 UDP 与串口是否可同时接收 UniRC 通道数据。
- [ ] 3.6 验证 UniRC `freq = 0` 是否会影响串口输出；未验证通过前，退出主控页不得默认发送 `freq = 0`。

## 4. 主屏 UI

- [ ] 4.1 按 `docs/genisdog_main_screen_layout.md` 实现全屏主控页骨架。
- [ ] 4.2 实现顶部运动模式 overlay，支持普通、原地、楼梯模式。
- [ ] 4.3 实现左侧低速、中速、高速三段垂直速度选择器。
- [ ] 4.4 实现底部动作组展开/收缩，开关按钮不发送机器人命令。
- [ ] 4.5 实现电量和机器状态 overlay，绑定 `RobotStateMessage`。
- [ ] 4.6 正式发布前替换 GenisDog 抓取素材；如使用 imagegen，最终资源必须是透明背景 PNG。

## 5. 控制命令

- [ ] 5.1 实现接管、释放、手动/自动模式和运动模式命令。
- [ ] 5.2 实现连续 `COMMAND_CODE_MOVE` 发送和零速度保护。
- [ ] 5.3 实现站立、卧倒、匍匐、锁定、爬高墙、扭一扭、过窄墙等动作命令。
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
