## Why

现有遥控 App 只直连 `legged_driver`，只能切换底层 AppMode，无法确认机器人主控是否真正完成模式切换，也无法在建图过程中观察实时栅格地图和机器人位置。操作者因此不能判断建图覆盖范围、轨迹和数据是否正常，需要建立遥控器到机器人主控的独立闭环。

## What Changes

- 新增到机器人主控 `tcp://<robot>:33446` 的 Protobuf + ZMQ DEALER 会话，与现有 `legged_driver:33445` 手动遥控连接并存。
- 同步机器人主控协议，完成鉴权、心跳、断线重连、状态订阅、命令结果和时间同步交互。
- AppMode 切换改由机器人主控统一编排；界面只在收到对应命令结果和权威状态后确认切换成功。
- 新增建图工作区，显示建图状态、实时 OccupancyGrid、机器人在 `map` 坐标系中的位置与朝向，以及地图新鲜度和异常原因；地图区域支持点击铺满工作区，再次点击还原。
- 对实时地图分片执行同帧重组、zlib 解压、SHA-256 校验、尺寸和值域校验；丢片或校验失败时请求主控重发最新完整帧。
- 新增开始建图、结束建图、保存和放弃入口，并保留 RViz 手动启动、测试节点及现有低延迟遥控链路。

## Capabilities

### New Capabilities

- `controller-mapping-workflow`: 遥控 App 与机器人主控的会话、AppMode 闭环、建图命令、实时地图和位姿显示能力。

### Modified Capabilities

无。

## Impact

- 影响 Android App 的协议生成、ZMQ 生命周期、控制器状态模型、设置持久化和 Compose 主界面。
- 新增机器人主控协议真源副本，来源为 `sar_legged_robot/src/sar_robot_controller/proto/robot_controller.proto`；`legged_driver/proto/message.proto` 仍是手动遥控协议真源。
- 新增 zlib 解压和哈希校验，但不改变现有摇杆输入、移动安全保护、视频播放和 `legged_driver` 连接语义。
