# Kotlin 遥控 App 重写方案

## 目标

重写一个新的 Android 遥控 App，使用 Kotlin 实现，主页面布局参考 GenisDog 当前主控页，通信协议套用 `/home/jiang/code/legged_driver`。现有 `LeggedJoystick` 只作为依赖选型和历史参考，不作为 UI 与业务结构的主依据。

新方案不接入 Skydroid/G20/AR8030 这类厂商遥控 SDK。当前正式移动输入来自 UniRC UDP 外部摇杆；屏幕只负责模式、速度、动作、状态和调试入口，不做触屏虚拟摇杆。

## 已整理素材

可用素材已从 GenisDog APK 和当前运行界面整理到 `docs/assets/genisdog/`。

| 文件 | 用途 |
| --- | --- |
| `main-reference.png` | 主控页布局参考，包含全屏视频背景、顶部模式栏、左侧速度、右侧工具、底部动作栏 |
| `settings-reference.png` | 设置页视觉参考，仅作灰色面板和控件风格参考 |
| `icon_stand*.png` | 站立动作按钮 |
| `icon_crawl*.png` | 匍匐动作按钮 |
| `icon_lie_down*.png` | 卧倒动作按钮 |
| `icon_small_spinning_top*.png` | 扭一扭动作按钮 |
| `icon_high_platform*.png` | 爬高墙动作按钮 |
| `icon_slim*.png` | 过窄墙动作按钮 |
| `icon_lock*.png` | 锁定动作按钮 |
| `icon_btn_speed_*.png` | 低速/中速/高速按钮视觉素材 |
| `icon_light*.png` | 补光灯按钮视觉素材 |
| `icon_photo*.png`, `icon_video*.png` | 拍照/录像按钮视觉素材 |
| `icon_setting.png`, `icon_back.png` | 设置和返回按钮 |
| `icon_mode_*.png`, `icon_title_bg.png`, `icon_speed_bg.png` | 模式、标题和速度状态装饰素材 |

未纳入方案的素材：充电桩、厂商配网、厂商 SDK 状态、原遥控器硬件按键映射、AR8030/Skydroid 图标和逻辑、`icon_over_mouse*.png`。`过挡鼠板` 在 `legged_driver` 官方 SDK 中没有对应接口，新 App 不实现。

抓取素材只用于内部联调和布局还原。正式发布前需要替换为自有或已授权素材；可以使用 imagegen 生成部分图标，但项目内最终使用的文件必须是透明背景 PNG。生成流程优先使用纯色背景生成，再用本地去色键工具转换为带 alpha 通道的 PNG，不能直接把带纯色背景的生成图放进 App。

## 技术路线

| 方面 | 决策 |
| --- | --- |
| 语言与 UI | Kotlin + Jetpack Compose |
| 构建系统 | Gradle 9.6.0 + Android Gradle Plugin 9.2.1 |
| 日志 | Timber |
| 协议 | 以 `/home/jiang/code/legged_driver/proto/message.proto` 为唯一真源 |
| Protobuf | Wire 生成 Kotlin 类型 |
| 网络 | JeroMQ，Android 端作为 ZMQ DEALER 客户端 |
| 服务端 | `legged_driver` 的 ROUTER 服务，默认监听 `tcp://0.0.0.0:33445` |
| App 端设备类型 | `DEVICE_TYPE_REMOTE_CONTROLLER` |
| 控制输入 | UniRC UDP 外部摇杆、屏幕按钮、开关 |
| 视频 | 不属于 `legged_driver` 协议，单独作为可插拔视频源处理 |

当前仓库里的 `proto/message.proto` 是旧版协议，字段包括 `MODE_SET`、`VELOCITY_COMMAND` 等；`legged_driver` 当前协议已经变为 `COMMAND_REQUEST`、`SUBSCRIPTION_REQUEST`、`ROBOT_STATE` 等结构。新 App 不应沿用当前仓库旧 proto，应从 `legged_driver/proto/message.proto` 同步或引用生成。

### 工程和依赖升级策略

新 App 继续使用当前仓库的单 `app` 模块工程壳，不新建独立 Android 工程。旧 UI、旧协议、旧虚拟摇杆和厂商相关入口不作为新实现依赖；后续实现时先把旧主流程从新主流程隔离，再按 `protocol`、`transport`、`domain`、`input`、`ui`、`media`、`settings` 分层重建。

构建栈先升级到当前稳定线：Gradle 9.6.0、Android Gradle Plugin 9.2.1、Kotlin 2.4.0、Compose BOM 2026.06.00、Wire 6.4.0，并把 `compileSdk` 升到 37 以满足最新 AndroidX 元数据要求。`targetSdk` 暂不随本次升级改变，避免引入和依赖升级无关的运行时行为变化。

依赖升级只采用稳定版；Maven metadata 中标记为 alpha、beta、RC、EAP、snapshot 或 nightly 的版本不进入第一版。AGP 9 已内置 Kotlin 支持，不再应用 `org.jetbrains.kotlin.android` 插件；旧的 `kotlinOptions` 和 `applicationVariants` DSL 已迁移到 Kotlin `compilerOptions` 和 Android Components API。

## 协议接入要点

Android 端连接 `legged_driver` 服务时，需要做四件事：

| 动作 | 说明 |
| --- | --- |
| 建立连接 | 使用 ZMQ DEALER 连接服务端 endpoint，客户端 identity 使用遥控器设备 ID |
| 心跳 | 每 1000ms 发送 `MESSAGE_TYPE_HEARTBEAT` |
| 订阅状态 | 启动后订阅 heartbeat、connection_state、app_mode_state、robot_state、motion_data、fault_data、odometry |
| 命令发送 | 所有按钮和摇杆最终封装成 `MESSAGE_TYPE_COMMAND_REQUEST` |

消息外层统一是 `LeggedDriverMessage`，必须包含时间戳、设备类型、设备 ID、消息类型、payload 和 CRC32。CRC32 计算方式要和 C++ `MessageUtils` 一致：计算前将 `crc32` 置 0，序列化后计算，再写回消息。

建议连接状态机：

| 状态 | 触发 |
| --- | --- |
| Disconnected | 未连接或主动断开 |
| Connecting | 正在创建 socket 并发出首次心跳 |
| Handshaking | 等待服务端 heartbeat 或 connection_state |
| Connected | 收到有效服务端状态 |
| Reconnecting | 心跳超时、socket 异常或网络切换 |

## 控制映射

主控页先做手动控制闭环，围绕 `COMMAND_CODE_TAKE_CONTROL`、`COMMAND_CODE_SET_APP_MODE`、`COMMAND_CODE_MOVE`、动作命令和灯光命令。

| UI 控件 | 协议命令 |
| --- | --- |
| 接管控制 | `COMMAND_CODE_TAKE_CONTROL` |
| 释放控制 | `COMMAND_CODE_RELEASE_CONTROL` |
| 手动/自动模式 | `COMMAND_CODE_SET_APP_MODE` |
| 低速/中速/高速 | `COMMAND_CODE_SET_SPEED_LEVEL` |
| 底部动作组开关 | 本地 UI 状态 `actionsExpanded`，不发送协议命令 |
| 运动模式：普通/原地/楼梯 | `COMMAND_CODE_SET_SPORT_MODE` |
| 外部左手摇杆前后 | `MoveCommandParams.forward_back` |
| 外部左手摇杆左右 | 内部 `strafe_right`，发送时映射到 `MoveCommandParams.left_right = -strafe_right` |
| 外部右手摇杆转向 | 内部 `yaw_right`，发送时映射到 `MoveCommandParams.yaw = -yaw_right` |
| 站立 | `COMMAND_CODE_STAND_UP` |
| 卧倒 | `COMMAND_CODE_LIE_DOWN` |
| 匍匐 | `COMMAND_CODE_CRAWL` |
| 爬高墙 | `COMMAND_CODE_CLIMB` |
| 扭一扭 | `COMMAND_CODE_GAIT` |
| 过窄墙 | `COMMAND_CODE_SLIM` |
| 锁定 | `COMMAND_CODE_LOCKED` |
| 前补光灯 | `COMMAND_CODE_FRONT_LIGHT` |
| 后补光灯 | `COMMAND_CODE_BACK_LIGHT` |
| 自动补光 | `COMMAND_CODE_AUTO_MODE_LIGHT` |
| 头部控制 | `COMMAND_CODE_CONTROL_HEAD` |
| 高低姿态 | `COMMAND_CODE_HIGH_LOW_STANCE` |

移动命令必须做连续发送。`legged_driver` 服务端有 200ms 速度超时保护，所以 App 摇杆按下时建议以 20Hz 到 30Hz 发送移动命令；摇杆释放、页面暂停、断连、失去焦点时立即发送零速度并停止循环。

## 摇杆输入接入方案

`docs/joystick_protocol.md` 记录的是 UniRC 10 Pro 原始通道帧协议。这个协议暂时不要直接耦合到 `legged_driver` 命令层，应先落到 App 自己的输入层，再由输入层统一生成移动意图。

### 分层边界

| 层 | 职责 |
| --- | --- |
| 原始输入源 | 第一版只接收 UniRC UDP 通道帧 |
| 输入解析 | 校验帧、提取 16 路通道、记录时间戳和序列号 |
| 输入归一化 | 把 1050 到 1950 的通道值转换为 -1.0 到 1.0，并处理死区、反向、限幅 |
| 输入状态 | 处理外部摇杆输入超时、链路自恢复和调试状态 |
| 控制意图 | 输出 `forward_back`、`strafe_right` 和 `yaw_right`，方向按操作者直觉表达 |
| 协议发送 | 按 `legged_driver` SDK 符号转换为 `MoveCommandParams`，并发送 `COMMAND_CODE_MOVE`、动作命令和零速度保护 |

这样即使后面决定把摇杆数据做成 Android 广播，广播也只能作为未来“单采集者分发”的原始输入来源，不进入第一版正式实现，不影响 UI、控制权、ZMQ 协议和安全策略。

### 多 App 分发未来方案

第一版不实现 Android 广播输入，也不为其他 App 做摇杆数据分发。如果后续确实需要共用摇杆数据，再把广播作为单采集者分发方案的一部分评估，并约束为显式 action、固定 package、签名级权限或同签名应用，避免其他应用伪造摇杆数据。广播接收器不能直接发送机器人命令，只能更新输入层状态；真正的命令发送仍由控制权、安全状态和移动命令循环统一决定。

当前本机探测结论是：UDP 同端口复用不能让两个进程都收到同一份单播数据。因此多 App 共用摇杆数据时，不应设计为“多个 App 绑定同一个本地 UDP 端口”。优先验证 UniRC 是否支持不同客户端端口分别订阅；如果不支持，则用一个采集者独占 UDP 或串口，再通过绑定服务或受限广播分发。

### 通道映射

UniRC 通道默认值范围为 1050 到 1950，中位为 1500。初版不要把物理通道写死到机器人运动轴，建议提供可配置映射并带默认值：

| 机器人输入 | 默认候选通道 | 说明 |
| --- | --- | --- |
| forward_back | CH3 | 左手前后 |
| strafe_right | CH4 | 左手左右平移，右推为正的内部意图 |
| yaw_right | CH1 | 右手转向，右推为正的内部意图 |
速度等级只由屏幕左侧速度选择器设置，不解析 L1/L2/L3，也不从外部摇杆通道切换速度档。

发送 `COMMAND_CODE_MOVE` 前必须按 `legged_driver` SDK 语义转换符号：`forward_back` 正数仍为前进；`left_right` 正数是左平移，所以内部 `strafe_right` 要取负；`yaw` 正数是左旋转，所以内部 `yaw_right` 要取负。

输入归一化建议使用可配置参数：

| 参数 | 用途 |
| --- | --- |
| center | 通道中位，默认 1500 |
| min/max | 通道最小/最大，默认 1050/1950 |
| dead_zone | 中位附近死区，避免摇杆轻微抖动 |
| invert | 单轴反向配置 |
| stale_timeout_ms | 外部摇杆数据超时后强制输出零速度 |

外部摇杆输入频率可以高于移动命令发送频率。输入层保留最新帧，移动命令循环仍按 20Hz 到 30Hz 发送；超过超时时间没有新帧时，应立即把运动轴置零。

## 主页面布局

主页面按横屏优先设计，参考 `docs/assets/genisdog/main-reference.png`。
详细坐标和层级规格见 `docs/genisdog_main_screen_layout.md`。

| 区域 | 内容 |
| --- | --- |
| 背景层 | 视频画面或占位画面，全屏铺满 |
| 顶部左侧 | 返回/退出控制、连接状态 |
| 顶部中间 | 运动模式选择，默认普通模式，支持普通/原地/楼梯 |
| 顶部右侧 | 网络状态、补光灯、拍照/录像、设置入口 |
| 左侧中部 | 速度等级按钮，显示低速/中速/高速；点击后显示三段垂直速度选择器 |
| 左侧下部 | 当前线速度读数 |
| 底部居中 | 第一个按钮负责展开/收缩动作组；展开后显示站立、匍匐、卧倒、扭一扭、爬高墙、过窄墙、锁定 |
| 状态浮层 | 电量、故障、控制权、停止保护状态，用小型 toast/banner 呈现 |

视觉风格可以继承 GenisDog 主控页的关键特征：暗色半透明浮层、青色高亮、圆形图标按钮、底部半透明动作条、顶部居中的模式胶囊。不要照搬充电桩和厂商设置页的信息结构。

## 页面与模块

建议新 App 结构：

| 模块 | 职责 |
| --- | --- |
| `protocol` | Wire 生成类型、CRC32、消息 envelope 构造、命令封装 |
| `transport` | ZMQ DEALER 客户端、发送队列、接收循环、重连、心跳 |
| `domain` | 控制权、AppMode、速度等级、运动模式、动作命令、状态缓存 |
| `input` | 外部摇杆通道接收、按钮节流、输入超时、自恢复、移动命令循环、零速度保护 |
| `ui` | Compose 主控页、设置页、状态浮层、素材加载 |
| `media` | 可选视频流播放，和控制协议解耦 |
| `settings` | 机器狗 IP、端口、视频地址、工程调试参数 |

`transport` 层不直接依赖 Compose；UI 只观察 domain 层状态并发出意图。这样后续可以替换视频、输入设备或协议细节，不影响主页面。

## 状态与安全策略

| 场景 | 策略 |
| --- | --- |
| App 启动 | 默认不发送运动命令，只连接和订阅状态 |
| 用户点击接管 | 发送 take control，成功后允许摇杆输出 |
| 未接管控制 | 动作按钮可以禁用或弹出提示，移动命令必须拦截 |
| 摇杆移动 | 应用死区、协议符号转换和安全 clamp 后发送 move；速度档只通过 `COMMAND_CODE_SET_SPEED_LEVEL` 生效 |
| 摇杆释放 | 立即发送零速度 |
| App 进入后台 | 发送零速度，暂停连续命令，必要时释放控制 |
| 连接中断 | 清空发送队列，进入重连；UI 标红或置灰控制区 |
| 停止输出保护 | 摇杆释放、页面后台、断连、失去控制权时停止移动输出，依赖 `driver` 侧速度超时保护停止机器人 |
| 故障状态 | 高级别故障时禁用运动按钮，保留释放控制入口 |

输入层只负责把外部摇杆限制到 `[-1.0, 1.0]` 并做死区处理，不按低/中/高速做二次倍率缩放。速度档已经由 `COMMAND_CODE_SET_SPEED_LEVEL` 和 `driver` 侧限速语义处理，避免 App 和 driver 重复缩放。

## 设置页

设置页不要复刻 GenisDog 的厂商项，只保留新 App 需要的内容：

| 设置项 | 说明 |
| --- | --- |
| 机器狗地址 | ZMQ 服务端 IP |
| ZMQ 端口 | 默认 33445 |
| 视频地址 | 可选，和控制协议解耦 |
| 工程调试入口 | 外部摇杆 CH1 到 CH16 原始值、轴值、UDP 状态、串口探测 |
| 调试信息 | 显示原始摇杆值、最后命令、连接状态、最近错误 |

## 实施顺序

1. 固化协议真源：把 `legged_driver/proto/message.proto` 同步到新 App，重新生成 Wire Kotlin 类型。
2. 重写协议工具：按 C++ `MessageUtils` 实现设备 ID、时间戳、CRC32、heartbeat、subscription、command request。
3. 重写 ZMQ 客户端：保留 JeroMQ，使用 DEALER、独立收发循环、心跳、重连和状态回调。
4. 做主控页静态骨架：横屏强制、全屏背景、顶部栏、左侧速度三段选择器、底部动作组展开/收缩、右侧工具区。
5. 接入输入层：接入 UniRC UDP 外部摇杆通道数据，实现死区、归一化、协议符号转换、输入超时、自恢复、连续发送和释放归零。
6. 接入动作按钮：先完成站立、卧倒、匍匐、锁定、速度等级、运动模式、灯光。
7. 接入状态订阅：显示连接、AppMode、RobotState、Fault、MotionData。
8. 接入设置页：支持 IP、端口、视频地址保存，变更后重连；工程调试参数仅在用户明确保存为默认时持久化。
9. 做联调模式：先连 `legged_driver` mock 服务，再连真实机器狗环境。
10. 收尾安全测试：后台、断网、杀进程、摇杆释放、控制权丢失都要验证停止输出保护和 UI 状态。

## 主要风险

| 风险 | 处理 |
| --- | --- |
| 当前仓库 proto 过旧 | 不复用旧 proto，以 `legged_driver` 当前 proto 为准 |
| 视频不在协议内 | 单独配置视频地址，第一阶段不阻塞控制闭环 |
| 外部摇杆链路异常 | 检测帧率、超时和自恢复次数，持续失败时停止移动输出 |
| 断连后继续运动 | App 端释放发送零速度，服务端已有 200ms 超时保护 |
| 命令频率过低 | 移动命令保持 20Hz 到 30Hz |
| 素材授权不明确 | 素材仅作为内部参考和临时开发资源；正式发布前替换为自有资产 |

## 推荐结论

新 App 不要从 GenisDog 反编译源码继续改，也不要复用厂商遥控器 SDK。更稳的路线是：用 GenisDog 主页面做视觉和交互参考，用整理出的图标素材快速搭建第一版 UI，用 `legged_driver` 当前 proto 和 ZMQ 协议实现完整控制闭环。这样后续协议变化只影响 `protocol/transport/domain`，不会把 UI 绑死在厂商 App 或旧项目结构上。
