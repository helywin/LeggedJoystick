## Context

项目要做的是新的机器狗遥控 App，而不是继续复用 GenisDog 或原厂遥控器 SDK。已有资料包括：

- `docs/kotlin_remote_app_plan.md`：总体方案。
- `docs/genisdog_main_screen_layout.md`：GenisDog 主屏布局和状态抓取规格。
- `docs/joystick_protocol.md`：UniRC 10 Pro 摇杆通道协议。
- `/home/jiang/code/legged_driver/proto/message.proto`：目标控制协议真源。

GenisDog APK 是 Flutter 自绘界面，不能反编译为可直接复用的 Kotlin 代码。它适合作为视觉和交互参考，不适合作为代码基础。

## Goals / Non-Goals

**Goals:**

- 使用 Kotlin 和 Jetpack Compose 重写主控 App。
- 用 GenisDog 主屏作为布局参考，实现可操作的遥控主界面。
- 接入 `legged_driver` ZMQ 协议，完成控制权、心跳、订阅、移动命令和动作命令。
- 建立统一输入层，支持触屏虚拟摇杆，并为 UniRC 通道数据和多 App 共用保留入口。
- 保证零速度、安全断连、输入超时、后台暂停等保护。

**Non-Goals:**

- 不复用 GenisDog Flutter/Dart 代码。
- 不接入 Skydroid、G20、AR8030 等厂商遥控 SDK。
- 不实现充电桩、厂商配网、建图巡航等非手动遥控闭环功能。
- 不把 `reverse/` 目录内容提交到仓库。

## Decisions

### 协议层

- Android 端作为 ZMQ DEALER 客户端连接 `legged_driver` ROUTER 服务。
- Wire 根据 `legged_driver/proto/message.proto` 生成 Kotlin 类型。
- `LeggedDriverMessage` 的时间戳、设备类型、设备 ID、payload 和 CRC32 由 `protocol` 模块统一构造。
- 移动命令以 20Hz 到 30Hz 连续发送，释放或异常时立即发送零速度。

### 输入层

- 触屏虚拟摇杆、UniRC 通道帧、可选 Android 分发数据都进入 `input` 层。
- 输入层负责帧校验、通道解析、归一化、死区、反向、限幅和输入仲裁。
- 同端口 UDP 复用已经本机实测不可依赖：20 个单播测试包全部进入后绑定接收者，另一个接收者收到 0 个。
- 多 App 共用优先验证不同客户端 UDP 端口订阅；若 UniRC 只支持单订阅者，则使用一个采集者独占 UDP 或串口，再通过绑定服务或受限广播分发。

### UI 层

- 主屏使用全屏 `Box` 叠层。
- 顶部保留返回、模式、网络、状态和设置入口。
- 左侧速度按钮使用低速、中速、高速三挡垂直选择器。
- 底部第一个按钮只控制动作组展开/收缩，不发送机器人命令。
- 电量和机器状态面板映射到 `RobotStateMessage`。

### 设置层

- 设置页保留机器狗地址、ZMQ 端口、视频地址、速度限幅、摇杆死区、输入来源、通道映射和调试信息。
- 不保留厂商配置项和充电桩相关项。

## Risks / Trade-offs

- GenisDog 素材授权不明确，短期仅用于内部参考，正式发布需要替换。
- UniRC UDP 是否支持多客户端订阅仍需在真实网络环境验证。
- 串口和 UDP 是否可同时输出也需在设备上验证。
- `legged_driver` proto 与仓库旧 proto 差异较大，必须先完成协议真源同步，否则后续实现会返工。
