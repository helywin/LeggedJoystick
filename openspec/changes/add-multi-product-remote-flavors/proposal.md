## Why

巡检产品与救援产品需要使用不同的模式权威和业务功能，但两个遥控 App 还要同时安装在同一台遥控器上。当前单一 applicationId、单一连接组合和 SAR 偏向的控制器逻辑无法满足该部署方式。

## What Changes

- 增加 `generalRobot` 与 `sarRescue` 两个 product flavor，并使用不同 applicationId、应用名和 APK 名。
- 使用一套 Kotlin、Compose、输入和驱动协议代码，通过集中式产品策略组合功能。
- general 版保留基础遥控功能，只增加 AUTO/MANUAL 切换，不创建 NX 连接，也不显示建图导航功能。
- SAR 版保留驱动与 NX 双连接和完整建图导航功能，不显示模式切换，也不直接发送 `SET_APP_MODE`。
- 同步 driver 新协议，两个 flavor 使用不同产品身份并等待服务端准入。

## Capabilities

### New Capabilities

- `multi-product-remote-flavors`: 定义两个可共存安装包和共享代码下的产品能力边界。

### Modified Capabilities

- `kotlin-remote-app`: 驱动连接增加产品身份与服务端准入，主屏入口按产品策略组合。

## Impact

- 受影响代码：Gradle flavor、应用资源、控制器组合、ZMQ 客户端、主屏 TopHud 和协议测试。
- 安装行为：两个 APK 使用不同 applicationId，可同时安装且数据目录相互隔离。
- 兼容边界：必须连接同步升级后的 `legged_driver`，不兼容旧协议。
