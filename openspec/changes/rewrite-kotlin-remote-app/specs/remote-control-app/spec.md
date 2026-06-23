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

### Requirement: 主屏必须复刻 GenisDog 的核心遥控布局

主屏 MUST 参考 `docs/genisdog_main_screen_layout.md`，保留全屏视频背景、顶部模式栏、左侧速度区、右侧工具列、底部动作组和状态 overlay。

#### Scenario: 速度选择
- **WHEN** 用户点击左侧速度按钮
- **THEN** App 必须显示低速、中速、高速三段垂直选择器，并通过 `COMMAND_CODE_SET_SPEED_LEVEL` 更新速度等级

#### Scenario: 动作组收缩
- **WHEN** 用户点击底部第一个动作组开关
- **THEN** App 只改变本地 `actionsExpanded` 状态，不得向机器狗发送动作命令

### Requirement: 输入层必须处理 UniRC UDP 外部摇杆

App MUST 将 UniRC UDP 通道帧进入输入层，再输出标准控制意图；当前版本不得把触屏虚拟摇杆或 Android 广播作为正式移动输入。

#### Scenario: 外部摇杆移动
- **WHEN** App 收到有效 UniRC `CMD_ID = 0x42` 通道帧
- **THEN** 输入层必须应用死区、归一化、协议符号转换和安全 clamp，然后由移动命令循环发送 `COMMAND_CODE_MOVE`

#### Scenario: 外部摇杆通道数据
- **WHEN** App 收到 UniRC `CMD_ID = 0x42` 通道帧
- **THEN** 输入层必须校验帧头、长度、命令号和 CRC16，并解析 CH1 到 CH16

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

#### Scenario: 退出主控页
- **WHEN** 用户退出主控页或停止前台服务
- **THEN** App 必须停止本 App 移动输出和输入消费；在验证 UniRC `freq = 0` 不影响串口输出前，不得默认发送 `freq = 0` 关闭通道输出

### Requirement: 项目文档和注释必须使用中文

项目文档和新增代码注释 MUST 使用中文；OpenSpec 固定关键字、协议枚举、文件名、命令名和库名 MAY 保留英文。

#### Scenario: 新增 OpenSpec 内容
- **WHEN** 编写 proposal、design、tasks 或 spec 内容
- **THEN** 除 OpenSpec 固定关键字外，实际描述必须使用中文

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
