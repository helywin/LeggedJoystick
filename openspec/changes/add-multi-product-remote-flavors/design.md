## Context

现有 App 已包含基础遥控、视频、状态、建图导航和 NX 业务连接。巡检版只需要在基础遥控上增加 AppMode 按钮；救援版继续由 NX 主控管理模式和建图导航。两者应共享实现而不是复制工程或长期维护两个分支。

## Goals / Non-Goals

**Goals:**

- 生成两个能够同时安装的独立 APK。
- 用集中式策略控制产品身份、NX 连接、任务入口和模式按钮。
- 保持基础遥控、输入、视频和状态代码共享。
- 把服务端准入纳入驱动连接状态。

**Non-Goals:**

- 不复制 Kotlin 包目录、控制器或主屏。
- 不给巡检版加入建图、地图、定位和导航功能。
- 不允许 SAR 版直接请求 AUTO/MANUAL。

## Decisions

### 1. flavor 使用独立 applicationId

`generalRobot` 使用 `com.helywin.leggedjoystick.general`，`sarRescue` 使用 `com.helywin.leggedjoystick.sar`。`namespace` 继续共享，两个 flavor 用独立资源覆盖应用名。

### 2. 集中式产品策略

组合入口从 BuildConfig 构造唯一 `RemoteProductPolicy`。Controller、ZMQ 和 Compose 接收策略，不在业务代码中散落 flavor 名称判断。

### 3. 巡检版只组合基础遥控和模式按钮

general 版不创建 `RobotControllerClient` 的业务连接，不展示任务入口、建图和导航工作区。模式按钮直接向 driver 请求 AUTO/MANUAL，并等待最终命令结果和权威状态确认。

### 4. SAR 版模式由 NX 管理

SAR 版不呈现模式按钮，控制器没有直接 `SET_APP_MODE` 路径。取消导航等行为发送到 `33446`，driver 模式状态只用于显示和手动速度门禁。

## Risks / Trade-offs

- [共享 Controller 继续混入产品分支] → 用策略能力和专用协调器集中分流，并用架构测试禁止散落判断。
- [两个 App 同时争用遥控器输入] → 保留现有单采集者 UDP 转发约束；同时安装不等于允许两个 App 同时输出运动。
- [错误 APK 连接错误产品] → 服务端产品准入拒绝，UI 显示明确中文原因。

## Migration Plan

1. 同步新 driver 协议。
2. 增加 flavor、独立包名和产品资源。
3. 引入产品策略并重构连接组合和模式入口。
4. 分别构建、测试两个 flavor；旧单一 applicationId 不再发布。
