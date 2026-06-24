## ADDED Requirements

### Requirement: 新遥控 App 必须使用当前稳定构建栈

新遥控 App MUST 继续使用当前仓库单 `app` 模块工程壳，并使用当前稳定版 Gradle、Android Gradle Plugin、Kotlin、AndroidX、Compose 和 Wire；第一版 MUST NOT 引入 alpha、beta、RC、EAP、snapshot 或 nightly 依赖。

#### Scenario: 构建栈升级
- **WHEN** 维护新遥控 App 构建配置
- **THEN** 必须使用稳定版 Gradle wrapper、AGP、Kotlin、AndroidX、Compose BOM 和 Wire，并通过 Gradle 构建验证

#### Scenario: AGP 9 迁移
- **WHEN** 使用 AGP 9 或更高版本
- **THEN** 构建脚本不得继续应用 `org.jetbrains.kotlin.android`，并必须使用 Kotlin `compilerOptions` 和 Android Components API 替代旧 DSL

### Requirement: 新遥控 App 必须使用 legged_driver 协议

新遥控 App MUST 以 `/home/jiang/code/legged_driver/proto/message.proto` 为协议真源，通过 Wire 生成 Kotlin 类型，并通过 ZMQ DEALER 客户端连接 `legged_driver` 服务。

#### Scenario: 建立连接并订阅状态
- **WHEN** 用户进入主控页并配置了机器狗地址和端口
- **THEN** App 必须建立 ZMQ DEALER 连接、发送心跳并订阅连接状态、AppMode、RobotState、MotionData、FaultData 和 Odometry

#### Scenario: 发送控制命令
- **WHEN** 用户触发速度、模式、动作或移动输入
- **THEN** App 必须构造 `MESSAGE_TYPE_COMMAND_REQUEST` 并按 `legged_driver` 的 CRC32 规则发送

### Requirement: ZMQ 连接按钮必须能从失败状态恢复

新遥控 App MUST 把用户主动点击连接视为一次全新的连接尝试；即使上一次连接卡住、超时或失败，也不得要求用户重启 App 才能再次连接成功。

#### Scenario: 连接失败后再次点击连接
- **WHEN** 用户点击连接后未能建立有效 ZMQ 通道，随后再次点击连接
- **THEN** App 必须先彻底释放旧 socket、旧 context、旧线程任务、旧连接验证任务和旧发送队列，再创建新的 ZMQ 资源

#### Scenario: 连接中重复点击
- **WHEN** 连接状态仍为 `CONNECTING` 或连接验证仍在进行
- **THEN** App 不得创建第二套 ZMQ socket 和工作线程；必须忽略重复点击或显式取消旧尝试后再重建

#### Scenario: 连接成功后重新连接
- **WHEN** 用户在已连接状态下要求重新连接
- **THEN** App 必须先停止移动输出、发送必要的停止命令、关闭现有连接资源，然后再进入新的连接尝试

### Requirement: 主屏必须复刻 GenisDog 的核心遥控布局

主屏 MUST 参考 `docs/genisdog_main_screen_layout.md`，保留横屏主控背景、顶部模式栏、左侧速度区、右侧工具列、底部动作组和状态 overlay；在 `Standard-10inch_A2` 上不得覆盖右侧系统虚拟导航栏。

#### Scenario: 速度选择
- **WHEN** 用户点击左侧速度按钮
- **THEN** App 必须显示低速、中速、高速三段垂直选择器，并通过 `COMMAND_CODE_SET_SPEED_LEVEL` 更新速度等级

#### Scenario: 动作组收缩
- **WHEN** 用户点击底部第一个动作组开关
- **THEN** App 只改变本地 `actionsExpanded` 状态，不得向机器狗发送动作命令

#### Scenario: 右侧系统导航栏
- **WHEN** App 运行在 `Standard-10inch_A2` 横屏设备上
- **THEN** 主控页、设置页和视频页不得启用完全沉浸式全屏，内容区域必须保留右侧系统虚拟导航栏空间

### Requirement: 输入层必须处理 UniRC UDP 外部摇杆

App MUST 将 UniRC UDP 通道帧进入输入层，再输出标准控制意图；当前版本不得把触屏虚拟摇杆或 Android 广播作为正式移动输入。

#### Scenario: 输入源抽象
- **WHEN** 新增 UniRC、串口、其他 UDP 遥控器或后续单采集者分发输入
- **THEN** 新输入实现必须适配统一 `RemoteInputSource` 抽象，并输出 `RemoteInputSnapshot` 和操作者视角的 `MovementIntent`

#### Scenario: 外部摇杆移动
- **WHEN** App 收到有效 UniRC `CMD_ID = 0x42` 通道帧
- **THEN** 输入层必须应用死区、归一化和安全 clamp，输出操作者视角运动意图，然后由移动命令循环发送 `COMMAND_CODE_MOVE`

#### Scenario: 外部摇杆通道数据
- **WHEN** App 收到 UniRC `CMD_ID = 0x42` 通道帧
- **THEN** 输入层必须校验帧头、长度、命令号和 CRC16，并解析 CH1 到 CH16

#### Scenario: 输入链路独立验证
- **WHEN** 验证遥控器 UDP 或串口通道数据
- **THEN** 只验证 UniRC 输入源、帧解析和运动意图映射，不得把 ZMQ 连接状态作为输入链路是否可用的判断条件

#### Scenario: 移动方向符号
- **WHEN** 用户左手前推、左手右推或右手右推
- **THEN** 内部移动意图必须分别表示为前进正值、右平移正值和右转正值；发送 `MoveCommandParams` 时必须保持 `forward_back` 正数前进，并把右平移、右转分别转换为负的 `left_right` 和负的 `yaw`

#### Scenario: 速度档处理
- **WHEN** 用户切换低速、中速或高速
- **THEN** App 必须通过 `COMMAND_CODE_SET_SPEED_LEVEL` 更新速度等级，输入层不得再按速度档对 `COMMAND_CODE_MOVE` 做二次倍率缩放

### Requirement: 多 App 共用摇杆数据不得依赖 UDP 同端口复用

App MUST NOT 设计为多个进程绑定同一个本地 UDP 端口来共享单播摇杆流。

#### Scenario: 需要多个 App 共用摇杆数据
- **WHEN** UniRC 数据需要被多个 App 使用
- **THEN** 应优先验证不同客户端 UDP 端口分别订阅；若不可行，则必须使用单采集者独占 UDP 或串口，再通过绑定服务或受限广播分发

#### Scenario: 本机 UDP 桥不能等同于独立通道
- **WHEN** 设备通过 `com.siyi.udpservice` 在 `127.0.0.1:19856` 提供 UniRC UDP 输入
- **THEN** App 必须把它视为 `/dev/ttyHS3` 串口桥继续验证，不得在收到有效并行验证结果前认定它和直接串口读取互不影响

#### Scenario: 本机 UDP 桥和直接串口读取竞争
- **WHEN** 新 App 通过本机 UDP 桥读取 UniRC 通道数据
- **THEN** 其他 App 不得再直接读取 `/dev/ttyHS3` 作为并行摇杆输入；如需多 App 共用，必须改为单采集者分发

#### Scenario: 第一版不实现广播输入
- **WHEN** 开发第一版输入层
- **THEN** 不得实现 Android 广播输入或对外摇杆分发；广播只能作为后续单采集者分发方案重新评估

### Requirement: 移动控制必须具备零速度保护

App MUST 在输入释放、输入超时、断连、后台和失去控制权时停止连续移动输出，并发送零速度或停止速度输出，让 `driver` 侧超时保护接管停止。

#### Scenario: 输入超时
- **WHEN** 外部摇杆超过超时时间没有新帧
- **THEN** App 必须将运动轴置零并停止沿用旧通道值

#### Scenario: 页面进入后台
- **WHEN** App 进入后台或主控页失去焦点
- **THEN** App 必须发送零速度并暂停连续移动命令循环

#### Scenario: 连接后保持前台服务
- **WHEN** App 已连接机器狗或工程 Mock 链路
- **THEN** App 必须启动前台服务和常驻通知作为遥控链路保活锚点；连接断开或 Activity 销毁时必须停止该服务

#### Scenario: 退出主控页
- **WHEN** 用户退出主控页或停止前台服务
- **THEN** App 必须停止本 App 移动输出和输入消费；在验证 UniRC `freq = 0` 不影响串口输出前，不得默认发送 `freq = 0` 关闭通道输出

### Requirement: 项目文档和注释必须使用中文

项目文档和新增代码注释 MUST 使用中文；OpenSpec 固定关键字、协议枚举、文件名、命令名和库名 MAY 保留英文。

#### Scenario: 新增 OpenSpec 内容
- **WHEN** 编写 proposal、design、tasks 或 spec 内容
- **THEN** 除 OpenSpec 固定关键字外，实际描述必须使用中文

### Requirement: 工程 Mock 模式不得触发真实链路

工程 Mock 模式 MUST 只用于 UI、状态和交互联调；开启后不得连接真实 ZMQ，不得打开 UniRC UDP 桥，也不得向真实机器狗发送控制命令。

#### Scenario: 开启工程 Mock 模式
- **WHEN** 用户在设置页开启工程 Mock 模式并点击主屏连接
- **THEN** App 必须使用本地 Mock 输入源和本地状态更新进入已连接状态，不得创建 ZMQ 连接或发送 UniRC UDP 订阅

#### Scenario: Mock 模式下触发控制按钮
- **WHEN** 用户在工程 Mock 模式下接管控制权并触发模式、速度、动作、灯光、头部或站姿命令
- **THEN** App 只能更新本地 UI 状态和最后发送命令摘要，不得发送真实 `COMMAND_REQUEST`

### Requirement: 第一版功能提交必须通过验证门槛

第一版 App 功能提交前 MUST 通过 OpenSpec 校验、Gradle 构建、核心单元测试和必要的 UI 截图检查；协议或输入层变更 MUST 追加协议封包、CRC 和 UniRC 帧解析测试。

#### Scenario: 普通功能提交
- **WHEN** 提交第一版普通功能改动
- **THEN** 必须通过 `openspec validate rewrite-kotlin-remote-app --strict`、Gradle 构建和核心单元测试

#### Scenario: UI 功能提交
- **WHEN** 提交主屏或状态面板 UI 改动
- **THEN** 必须通过工程 mock 模式主屏截图检查

#### Scenario: 协议或输入层提交
- **WHEN** 提交协议封包、CRC、UniRC 输入解析或轴映射改动
- **THEN** 必须额外通过协议封包/CRC 测试和 UniRC 帧解析测试
