## ADDED Requirements

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

### Requirement: 输入层必须统一处理触屏和外部摇杆

App MUST 将触屏虚拟摇杆、UniRC 通道帧和可选 Android 分发数据统一进入输入层，再输出标准控制意图。

#### Scenario: 触屏摇杆移动
- **WHEN** 用户拖动触屏虚拟摇杆
- **THEN** 输入层必须应用死区、归一化、速度倍率和限幅，然后由移动命令循环发送 `COMMAND_CODE_MOVE`

#### Scenario: 外部摇杆通道数据
- **WHEN** App 收到 UniRC `CMD_ID = 0x42` 通道帧
- **THEN** 输入层必须校验帧头、长度、命令号和 CRC16，并解析 CH1 到 CH16

### Requirement: 多 App 共用摇杆数据不得依赖 UDP 同端口复用

App MUST NOT 设计为多个进程绑定同一个本地 UDP 端口来共享单播摇杆流。

#### Scenario: 需要多个 App 共用摇杆数据
- **WHEN** UniRC 数据需要被多个 App 使用
- **THEN** 应优先验证不同客户端 UDP 端口分别订阅；若不可行，则必须使用单采集者独占 UDP 或串口，再通过绑定服务或受限广播分发

### Requirement: 移动控制必须具备零速度保护

App MUST 在输入释放、输入超时、断连、后台和失去控制权时停止连续移动输出，并发送零速度或停止速度输出，让 `driver` 侧超时保护接管停止。

#### Scenario: 输入超时
- **WHEN** 外部摇杆超过超时时间没有新帧
- **THEN** App 必须将运动轴置零并停止沿用旧通道值

#### Scenario: 页面进入后台
- **WHEN** App 进入后台或主控页失去焦点
- **THEN** App 必须发送零速度并暂停连续移动命令循环

### Requirement: 项目文档和注释必须使用中文

项目文档和新增代码注释 MUST 使用中文；OpenSpec 固定关键字、协议枚举、文件名、命令名和库名 MAY 保留英文。

#### Scenario: 新增 OpenSpec 内容
- **WHEN** 编写 proposal、design、tasks 或 spec 内容
- **THEN** 除 OpenSpec 固定关键字外，实际描述必须使用中文
