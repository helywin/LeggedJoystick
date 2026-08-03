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

#### Scenario: 正常断开通知 driver
- **WHEN** 用户主动断开已建立或正在验证的 ZMQ 连接
- **THEN** App 必须在关闭 socket 前发送 `MESSAGE_TYPE_CLIENT_DISCONNECT`
- **AND** `legged_driver` 收到后必须立即删除该客户端 identity、订阅状态和相关连接记录，不得依赖心跳超时完成正常断开清理

### Requirement: 控制链路必须脱离 Activity 生命周期

新遥控 App MUST 将控制器、ZMQ 客户端、输入源和全局状态保存在进程级 `object` 中，Activity 重建不得销毁已建立的遥控连接。

#### Scenario: Activity 重建
- **WHEN** Activity 因后台恢复、配置变化或系统回收后重建
- **THEN** 新 Activity 必须复用同一个进程级控制器和状态实例，不得重新创建第二套 ZMQ 客户端或输入源

#### Scenario: Activity 销毁
- **WHEN** Activity 执行 `onDestroy()`
- **THEN** Activity 不得直接调用控制器 `cleanup()`、不得停止已连接链路对应的前台服务；只有用户主动断开连接或进程退出才释放控制资源

### Requirement: 连接后必须自动进入可控状态

`legged_driver` MUST 在 SDK 与机器狗连接成功后自动尝试接管控制权；Android App MUST 不要求用户点击接管按钮才能发送手动控制命令。

#### Scenario: driver 自动接管机器狗
- **WHEN** `legged_driver` 检测到 SDK 与机器狗从未连接变为已连接
- **THEN** driver 必须异步发送一次 `takeControl`，并记录成功或失败日志；同一个已连接周期内不得重复刷屏发送接管请求

#### Scenario: App 连接 driver 后可控
- **WHEN** Android App 的 ZMQ 连接验证成功
- **THEN** App 必须将本地控制权状态更新为已接管，并发送手动 AppMode 与低速速度档初始化命令

#### Scenario: App 不再需要接管点击
- **WHEN** 用户完成连接并触发速度、模式、动作或移动输入
- **THEN** App 不得因为用户没有点击接管按钮而拦截命令

#### Scenario: 主控页不显示手动接管入口
- **WHEN** 用户进入主控页
- **THEN** App 不得显示接管、释放或重试接管按钮；用户只需要使用连接按钮建立或断开 driver 链路

#### Scenario: 主控页在连接按钮左侧显示自动手动切换
- **WHEN** 用户进入主控页且已经建立 driver 连接并拥有控制权
- **THEN** 顶部状态栏必须在连接按钮左侧显示当前 AppMode 的自动/手动切换按钮
- **AND** 按钮必须直接显示“自动模式”或“手动模式”文字，不得只使用无法表达模式含义的通用机器人图标
- **AND** 用户点击按钮时必须在 `APP_MODE_AUTO` 与 `APP_MODE_MANUAL` 之间切换，并通过 `COMMAND_CODE_SET_APP_MODE` 请求 driver 更新模式
- **AND** 未连接、未拥有控制权或上一次模式请求尚未完成时，按钮必须不可用
- **AND** 按钮显示必须以 driver 回报的 `AppMode` 状态为准，不得把该按钮误作为接管或释放控制权入口

#### Scenario: App 打开后自动连接
- **WHEN** App 进程首次打开并完成控制器初始化
- **THEN** App 必须自动触发一次连接请求
- **AND** 如果用户在同一轮界面中手动断开，不得因为自动连接逻辑立即再次重连

### Requirement: 后台恢复后主屏视频必须重新显示

主屏 RTSP 视频组件 MUST 使用进程级 IJKPlayer 运行时管理视频资源，通过 `TextureView` 输出视频，在 Activity 从后台恢复后重新绑定视频输出并重新加载当前 RTSP 地址，避免背景视频黑屏、重复创建播放器输出或日志风暴。

#### Scenario: 后台再回前台
- **WHEN** 用户将 App 切到后台后再回到主屏
- **THEN** 背景视频和左上小视频必须复用稳定播放器槽位，重新 attach 当前播放器视图并恢复播放当前 RTSP 流

#### Scenario: 默认网络或 USB 链路变化后恢复视频
- **WHEN** Android 默认网络因为 Wi-Fi 重连、路由变化而切换，或用户拔插 USB/ADB 调试线
- **THEN** 背景视频和左上小视频必须重拉当前 RTSP 地址，不得停留在播放器错误或黑屏状态等待用户重启 App

#### Scenario: RTSP 失败后持续重试
- **WHEN** RTSP 播放器出现打开失败、播放错误、视频流结束、长时间未进入播放状态或播放后长时间没有新帧
- **THEN** App 必须持续重试加载当前 RTSP 地址，直到视频恢复播放、用户清空视频地址或组件离开前台

#### Scenario: RTSP 重连时显示转圈提示
- **WHEN** 主屏背景视频或左上小视频因为断流、启动超时、网络变化或画面长时间没有新帧进入重连状态
- **THEN** 对应视频区域必须保留当前 `TextureView` 历史画面并显示仅用于界面提示的转圈动画
- **AND** 顶部拍照截图必须仍然直接截取 `TextureView` 视频帧，不得把转圈动画或 Compose 覆盖层合成进图片

#### Scenario: 使用 TextureView 播放 RTSP
- **WHEN** 主屏显示背景视频和左上小视频
- **THEN** App 必须使用 `IjkMediaPlayer` 绑定 `TextureView` 播放 RTSP，不得继续使用 VLC 的 `SurfaceView` 输出路径

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

#### Scenario: 主屏双路视频
- **WHEN** 用户进入主控页
- **THEN** App 必须默认将 `rtsp://192.168.234.1:8554/front` 作为主屏背景显示，并将 `rtsp://192.168.234.1:8554/back` 显示在左上小视频窗口；设置模型直接使用 `headRtspUrl` 和 `tailRtspUrl`

#### Scenario: 主屏视频比例
- **WHEN** 主控页显示 1920x1080 RTSP 视频
- **THEN** 主屏背景视频必须优先铺满可用屏幕区域，不得出现视频自身黑边；左上小视频窗口必须使用 16:9 容器比例，避免控件比例导致黑边或变形

#### Scenario: 主屏视频源互换
- **WHEN** 用户点击左上小视频窗口
- **THEN** App 必须只在本地互换主屏背景和左上小视频窗口的视频源，不得依赖或修改 `RobotState.head_direction`

#### Scenario: 主屏拍照
- **WHEN** 用户点击顶部拍照按钮
- **THEN** App 必须截取当前主屏背景视频流和左上小视频流画面，上下拼接成一张图片并保存到系统相册，不得再通过顶部视频按钮跳转到独立视频页
- **AND** 任意一路视频 Surface 未准备好或截图失败时，该路画面必须使用黑色占位，仍然保存合成图片

#### Scenario: 头尾方向切换
- **WHEN** 用户点击右侧工具列的头尾方向切换按钮
- **THEN** App 必须发送 `COMMAND_CODE_REVERSE_HEAD_TAIL`，并由 `RobotState.head_direction` 驱动按钮状态显示；头尾方向切换不得直接改变主屏背景或左上小视频窗口的视频源

#### Scenario: 动作按钮选中态
- **WHEN** App 收到 `RobotState.motion_status`
- **THEN** 底部动作按钮必须按实际动作状态显示选中态，选中图标使用透明背景资源，不得显示逆向素材中的半透明圆形底

#### Scenario: 灯光状态来源
- **WHEN** 用户发送前灯、后灯或自动补光命令
- **THEN** 真实链路下 App 不得仅因命令入队成功就长期覆盖 UI 状态，最终显示必须以 driver 可订阅状态或命令完成确认后的状态为准

#### Scenario: 双电池状态显示
- **WHEN** 用户查看主控页并点击顶部电量按钮
- **THEN** 顶部电量按钮只能显示电池图标，不得直接显示单路电量数字
- **AND** 打开的状态浮层必须分别显示 `BatteryDataMessage.power1` 和 `power2` 对应的电池 1、电池 2 电量
- **AND** 某一路 `present1` 或 `present2` 为 false 时，该路必须显示不可用状态，不得伪造平均值

### Requirement: 输入层必须处理多厂商外部摇杆

App MUST 将思翼 UniRC UDP 或云卓 G20 RCSDK 通道数据送入统一输入层，再输出标准控制意图；当前版本不得把触屏虚拟摇杆或 Android 广播作为正式移动输入。

#### Scenario: 输入源抽象
- **WHEN** 新增 UniRC、串口、其他 UDP 遥控器或后续单采集者分发输入
- **THEN** 新输入实现必须适配统一 `RemoteInputSource` 抽象，并输出 `RemoteInputSnapshot` 和操作者视角的 `MovementIntent`

#### Scenario: 按设备自动选择输入源
- **WHEN** App 启动在 RCSDK 识别为 `DeviceType.G20` 的云卓 G20 上
- **THEN** 输入源 factory 必须选择 G20 RCSDK provider，不得启动思翼 UniRC UDP 桥
- **AND** 当设备不匹配已注册的厂商 provider 时，必须回退到思翼 UniRC UDP provider

#### Scenario: G20 主动读取通道
- **WHEN** G20 RCSDK 连接成功
- **THEN** 输入层必须通过 `RemoteControllerKey.KeyChannels` 以不快于 100ms 的周期主动读取通道，不得并发堆积未完成的读取请求
- **AND** 通道值必须按 G20 真机实测的最小值 `900`、中位值 `1500`、最大值 `2100` 做归一化、死区和限幅

#### Scenario: G20 不提供硬件速度档
- **WHEN** G20 输入源输出摇杆快照
- **THEN** `speedLevelRequest` 必须为空，低速、中速、高速只能由屏幕速度选择器通过 `COMMAND_CODE_SET_SPEED_LEVEL` 切换

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

#### Scenario: 原地模式右杆姿态俯仰
- **WHEN** 机器人处于 `SPORT_MODE_IN_PLACE` 且用户推动右杆上下
- **THEN** 输入层必须从右杆上下通道输出独立姿态俯仰意图，默认映射为 UniRC CH2，并按真机方向反向后使右杆上推为抬头正值
- **AND** 控制层必须通过 `COMMAND_CODE_CONTROL_HEAD` 连续发送姿态控制，`up_down` 正值表示抬头，右杆左右必须映射到 `left_right` 姿态左右控制
- **AND** 用户松开右杆、输入超时、页面后台、断连或失去控制权时必须停止连续姿态输出并发送 `CONTROL_HEAD(0, 0)`

#### Scenario: 速度档处理
- **WHEN** 用户切换低速、中速或高速
- **THEN** App 必须通过 `COMMAND_CODE_SET_SPEED_LEVEL` 更新速度等级，输入层不得再按速度档对 `COMMAND_CODE_MOVE` 做二次倍率缩放

#### Scenario: 外部遥控器左侧速度按钮
- **WHEN** 用户按下 UniRC 左侧 L1、L2 或 L3 按钮
- **THEN** 输入层必须按真机观测到的 CH5 离散值将 `1000`、`1200`、`1400` 分别解析为低速、中速、高速请求
- **AND** 控制层必须在按钮按下边沿调用速度档控制，不得由输入层直接拼装或发送底层协议帧
- **AND** 速度按钮只能触发 `COMMAND_CODE_SET_SPEED_LEVEL`，不得改变 `COMMAND_CODE_MOVE` 的轴值倍率
- **AND** App 启动或输入链路重建后的首个 CH5 速度请求只作为基线，不得覆盖接管后默认低速；只有后续 CH5 变化才允许触发速度档切换

#### Scenario: 原始 UDP 报文转发
- **WHEN** UniRC 输入源从 UDP 套接字收到任意 datagram
- **THEN** App 必须在解析前把该 datagram 的原始字节原样转发到本机可配置 UDP 端口
- **AND** 转发不得依赖 Android 广播、不得要求其他 App 绑定原 UniRC 输入端口、不得影响当前 App 的帧解析和运动安全保护
- **AND** 没有监听者或转发失败时不得中断当前 App 的输入源

### Requirement: 多 App 共用摇杆数据不得依赖 UDP 同端口复用

App MUST NOT 设计为多个进程绑定同一个本地 UDP 端口来共享单播摇杆流。

#### Scenario: 需要多个 App 共用摇杆数据
- **WHEN** UniRC 数据需要被多个 App 使用
- **THEN** 不得依赖同端口 UDP 复用、不同客户端 UDP 端口并发订阅或 UDP 桥加直接串口读取；必须使用单采集者独占 UDP 或串口，再通过本机 UDP 原始报文转发给其他监听端口

#### Scenario: 本机 UDP 桥使用最近客户端地址
- **WHEN** 多个本机 UDP 客户端端口同时向 `com.siyi.udpservice` 订阅 UniRC 通道帧
- **THEN** App 不得认定每个端口都能稳定收到完整通道流；该桥更接近最近客户端地址模型，不适合作为多 App 共享通道

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

#### Scenario: G20 RCSDK 断连
- **WHEN** RCSDK 报告 G20 断连、读取持续失败或 App 停止输入消费
- **THEN** 输入层必须停止通道轮询、清空旧摇杆意图并进入非运行状态，让控制层执行零速度保护

#### Scenario: 页面进入后台
- **WHEN** App 进入后台或主控页失去焦点
- **THEN** App 必须发送零速度并暂停连续移动命令循环

#### Scenario: 连接后保持前台服务
- **WHEN** App 已连接机器狗或工程 Mock 链路
- **THEN** App 必须启动前台服务和常驻通知作为遥控链路保活锚点；连接断开或 Activity 销毁时必须停止该服务

#### Scenario: 退出主控页
- **WHEN** 用户退出主控页或停止前台服务
- **THEN** App 必须停止本 App 移动输出和输入消费；第一版不得默认发送 `freq = 0` 关闭通道输出

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

第一版 App 功能提交前 MUST 通过 OpenSpec 校验、Gradle 构建、核心单元测试和必要的 UI 截图检查；协议或输入层变更 MUST 追加协议封包、CRC、UniRC 帧解析或 G20 通道映射测试。

#### Scenario: 普通功能提交
- **WHEN** 提交第一版普通功能改动
- **THEN** 必须通过 `openspec validate rewrite-kotlin-remote-app --strict`、Gradle 构建和核心单元测试

#### Scenario: UI 功能提交
- **WHEN** 提交主屏或状态面板 UI 改动
- **THEN** 必须通过工程 mock 模式主屏截图检查

#### Scenario: 协议或输入层提交
- **WHEN** 提交协议封包、CRC、UniRC 输入解析或轴映射改动
- **THEN** 必须额外通过协议封包/CRC 测试、UniRC 帧解析测试和本次涉及的厂商输入源测试
