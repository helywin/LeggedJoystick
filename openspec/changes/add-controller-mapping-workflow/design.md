## Context

见 [proposal.md](proposal.md) 的 Why。当前 App 的进程级控制器只维护一条到 `legged_driver:33445` 的连接，界面直接把底层 AppMode 当作业务模式。机器人主控已在 `33446` 提供独立的版本化 Protobuf 协议、权威运行状态、命令响应、时间同步和实时地图分片；手动摇杆仍必须走原驱动链路以保留延迟和 watchdog 特性。

## Goals / Non-Goals

**Goals:**

- 把业务运行态、底盘控制权和手动遥控连接状态拆开建模。
- 建立可测试的主控协议客户端、地图组装器、坐标转换和 Compose 展示。
- 在连接、慢消费者、丢片、重连和时间异常下保持内存有界且状态诚实。
- 保留现有视频、摇杆、安全停机和工程 RViz 链路。

**Non-Goals:**

- 本切片不实现地图列表、已保存地图预览、地图切换、初始位姿和导航选点。
- 不让高频摇杆数据绕行机器人主控。
- 不在 App 中解释或绕过主控 `allowed_actions` 门禁。
- 不以 Android 单元测试或工程 Mock 截图替代真机网络、NX 运行时和真实地图验收。

## Decisions

### 两条 ZMQ 连接独立管理

保留现有 `NewZmqClient` 连接 `legged_driver:33445`，新增 `RobotControllerClient` 连接 `sar_robot_controller:33446`。两者使用独立 context、I/O 线程、发送队列、连接状态和重连代次，统一汇入进程级 `ControllerState`。不复用一个 DEALER socket，因为服务端协议、身份、心跳和会话语义完全不同，复用会把故障域和队列阻塞耦合。

用户点击连接时同时启动两条连接。驱动连接决定移动输出是否可用，主控连接决定建图命令是否可用。断开操作关闭两者；单链路异常只清理该链路持有的状态。

### 主控协议使用独立真源副本

将 `sar_legged_robot/src/sar_robot_controller/proto/robot_controller.proto` 同步到 App 的 `proto/` 并由现有 Wire 流程生成 Kotlin 类型。包名保持 `sar.robot_controller.v1`，不把它合并进 `legged_driver` 协议，避免同名状态语义混淆。协议副本顶部记录真源路径和同步版本，测试比较关键字段编号和枚举值。

### AppMode 改为任务和权威控制权闭环

界面不再把驱动命令入队当作切换成功，也不提供无任务的裸 AUTO。主控快照中的 `control_owner` 是产品界面的权威来源：`REMOTE_MANUAL` 表示人工控制，`NAVIGATION_AUTO` 表示导航任务控制。人工接管通过主控命令完成取消目标、零速度和 MANUAL 确认；主控离线时仅保留直接 MANUAL 作为安全降级，禁止直接 AUTO。

建图时主控同时报告 `MAPPING_RUNNING + REMOTE_MANUAL`，因此操作者可以边走边观察地图。后续导航选点切片由 `START_NAVIGATION` 进入 AUTO，不需要增加一个破坏状态机语义的模式切换接口。

### 主控客户端使用单线程 socket 和请求登记表

JeroMQ socket 只在一个 I/O 线程读写。业务线程将不可变消息放入有界优先队列；心跳和地图补帧请求可以合并，命令请求不可被地图流挤出。客户端维护递增 request ID、session ID、连接 generation 和待响应表。收到响应时必须同时匹配 generation、session 和 request ID；重连时一次性清除旧请求并回调失败。

Hello 成功后立即保存完整快照。`TimeSyncChallenge` 以收到挑战前后的 Android UTC 纳秒时间构造 commit；最终只展示主控返回的 `TimeSyncStatus`，不把 App 本地发送成功视为 NX 已对时。

### 地图组装和解码保持纯 Kotlin 可测试

`MappingGridAssembler` 与 Android UI 解耦，只接受协议分片并输出不可变 `MappingGridFrame`。同一时刻只维护一个最新 frame sequence 的组装上下文，限制最大 16 MiB 未压缩数据、最大压缩字节、最大分片数和组装时长。每个分片重复到达时只有内容完全一致才忽略，否则整帧失败。

完整分片按 index 拼接后先校验压缩长度和 SHA-256，再使用 `Inflater` 解压，最后校验宽高乘积、未压缩长度和每个有符号字节的 `-1..100` 值域。失败保留上一完整帧并触发带最后完整帧号的 `GetMappingMapRequest`。新 frame sequence 到达时直接替换未完成旧帧，避免慢消费累积。

### 地图像素和机器人坐标采用确定变换

占用栅格保留 ROS 行序，不在协议层改写。生成显示位图时把未知、空闲和占用值映射为固定颜色，并将行号 `row=0` 翻到屏幕底部。机器人世界坐标先减去地图 origin，再乘 origin yaw 的逆旋转，除以 resolution 得到栅格坐标：

`grid_x = (cos(yaw0) * dx + sin(yaw0) * dy) / resolution`

`grid_y = (-sin(yaw0) * dx + cos(yaw0) * dy) / resolution`

屏幕 y 使用 `height - grid_y`。机器人屏幕朝向同时扣除地图 origin yaw 并翻转 y 轴。Canvas 使用同一个 content transform 完成居中缩放，因此栅格、位置和朝向不会各自漂移。

### 建图工作区作为主屏模式层

当 operation mode 进入建图准备、运行、复核或保存时，主背景切换为建图工作区；视频缩到可切换的小窗，现有摇杆输入和安全状态不变。地图在普通横屏中也作为主视觉，状态栏压缩为只常驻运行态、控制权、健康、位置、朝向和新鲜度的窄摘要；任务、对时、尺寸和丢弃计数放入按需展开的详情，不能长期挤占地图。底部操作只按 `allowed_actions` 启用。

新一轮建图开始时，主控和 App 同时清空上一轮地图与位姿；本轮首帧到达前显示等待状态。这样即使网络队列中仍有旧分片，界面也不会把上一张地图或上一坐标系位置误认为当前进度。

工程 Mock 生成固定小地图和运动轨迹，用于截图和 Compose 回归，但明确标记“工程 Mock”。真实模式不本地猜测状态，不允许按钮乐观跳转。

地图帧和位姿新鲜度统一使用 Android 进程内单调时钟记录本地接收时刻；界面刷新时取最新观测时刻计算年龄并将负值钳制为零，避免系统 UTC 调整或地图帧与界面计时器交错更新造成“时钟异常”误报。

地图画布自身作为全屏切换的唯一点击目标。普通工作区点击地图后只隐藏状态栏、视频小窗、摇杆和建图操作区，让同一份地图画布铺满父容器；全屏地图顶部叠加轻量的“再次点击还原”提示，再次点击画布恢复普通布局。该状态仅属于 Compose 展示层，不发送协议命令、不重建控制器，也不改变持续接收的地图帧和位姿。

### 设置与凭据边界

设置新增主控端口，默认与机器狗 IP 共用且端口为 `33446`；令牌通过部署配置写入，不显示在普通调试摘要和 Timber 日志中。第一版沿用 App 私有 SharedPreferences，但日志只输出 endpoint 和连接状态。

## Risks / Trade-offs

- [实时地图解压和位图生成造成 UI 卡顿] → 在后台 dispatcher 解压和生成像素，只向 Compose 原子发布最新完成帧，并丢弃过时工作。
- [地图全帧经 ZMQ 在弱网络下丢片或积压] → 分片校验、单帧组装、超时补拉和最新帧覆盖，命令队列优先于地图请求。
- [主控与驱动连接状态短时不一致] → 分栏显示两条链路，控制权只认主控快照；主控离线时只允许 MANUAL 安全降级。
- [协议副本漂移] → 关键字段契约测试和部署前双仓同步检查；不手写 wire 编解码。
- [工程 Mock 与真实 NX 地图或网络性能差异] → 遥控器实机 Mock 只证明设备侧布局、渲染和交互，最终验收仍必须连接 NX 并观察真实地图与位姿。

## Migration Plan

1. 先部署允许 `DEVICE_TYPE_ROBOT_CONTROLLER` 的 `legged_driver`，再部署更新后的 bridge 和主控。
2. 使用主控 mock server 验证 App 会话、模式闭环、地图丢片恢复和坐标渲染。
3. 在遥控器真机并行连接 33445/33446，保持 MANUAL 和零速度确认两条链路状态。
4. 启动建图后低速行走，核对 RViz `/map`、`/odom/slam_odom` 与 App 地图/位置一致，再验证结束、保存和放弃。
5. 回滚 App 时保持主控和驱动协议的追加字段；旧 App 继续只使用 33445，不影响 RViz 和原 launch。
