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
- 继续使用当前仓库单 `app` 模块工程壳，并升级到当前稳定构建栈。
- 用 GenisDog 主屏作为布局参考，实现可操作的遥控主界面。
- 接入 `legged_driver` ZMQ 协议，完成控制权、心跳、订阅、移动命令和动作命令。
- 建立统一输入层，第一版只支持 UniRC UDP 外部摇杆；当前不做触屏虚拟摇杆，也不实现 Android 广播输入。
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

- 第一版只有 UniRC UDP 通道帧进入 `input` 层；Android 广播只作为未来单采集者分发方案保留，不进入第一版实现。
- 输入层负责帧校验、通道解析、归一化、死区、反向、限幅、输入超时和链路自恢复。
- 内部移动意图按操作者直觉表达：前进、右平移、右转为正；协议发送层必须按 `legged_driver` SDK 语义转换为 `MoveCommandParams`，其中 `left_right` 正数是左平移，`yaw` 正数是左旋转。
- UI 速度档只发送 `COMMAND_CODE_SET_SPEED_LEVEL`，移动输入层不得按低/中/高速做二次倍率缩放。
- 同端口 UDP 复用已经本机实测不可依赖：20 个单播测试包全部进入后绑定接收者，另一个接收者收到 0 个。
- `Standard-10inch_A2` 上存在本机 `com.siyi.udpservice`，监听 `127.0.0.1:19856` 并桥接 `/dev/ttyHS3`；它可以作为当前 App 的默认 UDP 入口继续联调。实测表明它和直接读取 `/dev/ttyHS3` 会竞争同一条串口字节流，不适合作为两个 App 的独立输入通道。
- 不同客户端 UDP 端口已经实测：两个端口都能单独订阅，并发重复订阅时也能收到部分帧，但 `com.siyi.udpservice` 更接近最近客户端地址模型，不会稳定广播给多个端口。因此多 App 共用必须使用一个采集者独占 UDP 或串口，再通过绑定服务或受限广播分发。
- `freq = 0` 串口直测未发现会停止 `/dev/ttyHS3` 通道输出；第一版仍保持不主动发送 `freq = 0` 的保守策略，避免影响未知服务状态。

### UI 层

- 主屏使用横屏 `Box` 叠层，但 Activity 不启用 edge-to-edge，必须保留右侧系统虚拟导航栏空间。
- 顶部保留模式、连接、控制权、电量、拍照和设置入口；左上角不再显示 App 标题和输入状态文字。
- 主屏背景默认播放头部 RTSP 视频流 `rtsp://192.168.234.1:8554/front`，左上小视频窗口默认播放尾部 RTSP 视频流 `rtsp://192.168.234.1:8554/back`；设置模型使用 `headRtspUrl` 和 `tailRtspUrl` 两个明确字段。点击左上小视频窗口时，只在 App 本地互换主背景和小视频窗口的视频源。
- 板端相机流为 1920x1080。主屏背景优先铺满 `Standard-10inch_A2` 可用内容区域，允许轻微非等比拉伸以消除上下黑边；左上小视频窗口固定 16:9 容器比例并保持等比显示，避免控件比例自身制造黑边。
- 顶部拍照按钮截取主屏背景当前视频流画面，不再保留顶部视频跳转入口。
- 左侧速度按钮使用低速、中速、高速三挡垂直选择器。
- 底部第一个按钮只控制动作组展开/收缩，不发送机器人命令。
- 底部动作按钮使用 `RobotState.motion_status` 显示实际选中态，选中图标使用去除半透明圆形底后的蓝色透明资源。
- 右侧工具列保留灯光入口，并新增头尾方向切换按钮；旧头部控制和高低站姿面板从主屏入口移除。头尾方向切换只发送 `COMMAND_CODE_REVERSE_HEAD_TAIL` 并显示方向状态，不再驱动主背景和左上小视频互换。
- 电量和机器状态面板映射到 `RobotStateMessage`。
- 真实链路下灯光 UI 不再把“ZMQ 入队成功”当作最终状态；App 侧等待订阅状态或命令完成结果，`legged_driver` 真实后端需要把 SDK 灯光 ACK 同步到 `RobotState` 缓存并发布。

### 工程层

- 当前仓库保留单 `app` 模块，不新建独立 Android 工程。
- 构建栈使用稳定版：Gradle 9.6.0、Android Gradle Plugin 9.2.1、Kotlin 2.4.0、Compose BOM 2026.06.00、Wire 6.4.0。
- `compileSdk` 使用 37，以满足最新 AndroidX 依赖元数据要求；`targetSdk` 暂不因依赖升级同步改变。
- AGP 9 内置 Kotlin 支持，构建脚本不得继续应用 `org.jetbrains.kotlin.android` 插件。
- 连接成功后启动前台服务和常驻通知，作为后台长连接的进程保活锚点；当前切片不迁移 ZMQ 和输入控制归属，后续如需把链路完全托管到 Service，再单独做生命周期重构。
- 旧游戏手柄输入、旧触屏虚拟摇杆和相关调试组件保留源码，作为后续适配其他遥控器或调试输入的备选组件；第一版主流程不得引用这些组件，也不得把它们接入移动命令输出。
- 旧协议和厂商相关入口不作为新主流程依赖。

### 设置层

- 普通设置页保留机器狗地址、ZMQ 端口和视频地址；工程调试页保留摇杆死区、通道映射、轴反向、UDP 状态、串口探测和调试信息。
- 不保留厂商配置项和充电桩相关项。

## Risks / Trade-offs

- GenisDog 素材授权不明确，短期仅用于内部参考，正式发布需要替换。
- UniRC 多 App 共用不能依赖同端口复用、不同 UDP 客户端端口或 UDP 桥加直读串口；如果多个 App 要共用摇杆数据，必须走单采集者分发。
- 串口和本机 UDP 桥已验证会竞争同一条 `/dev/ttyHS3` 字节流。
- `freq = 0` 当前串口直测未发现会停止通道输出，但仍不把它作为第一版默认退出动作。
- `legged_driver` proto 与仓库旧 proto 差异较大，必须先完成协议真源同步，否则后续实现会返工。
- 最新稳定依赖可能带来 AGP/Gradle 迁移成本；已知迁移点包括移除 `org.jetbrains.kotlin.android`、迁移 `kotlinOptions` 和迁移旧 `applicationVariants` API。
