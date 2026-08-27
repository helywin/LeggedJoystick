## Purpose

定义巡检与救援两个遥控 App 在同一代码库中的安装身份、功能组合和模式权威边界。

## ADDED Requirements

### Requirement: 两个产品必须生成可共存安装包
工程 MUST 生成 `generalRobot` 和 `sarRescue` 两个 flavor，并 MUST 使用不同 applicationId、应用名和 APK 名；两个安装包的数据目录 SHALL 相互隔离。

#### Scenario: 在同一遥控器安装两个 APK
- **WHEN** 操作者依次安装 general 与 SAR release APK
- **THEN** Android 必须保留两个独立桌面入口且不得用后安装包覆盖先安装包

### Requirement: 两个产品必须共享基础遥控代码
两个 flavor SHALL 复用同一套输入、视频、状态、动作、灯光、ZMQ 传输和 Compose 主屏组件，MUST NOT 复制控制器或主屏实现。

#### Scenario: 修改公共摇杆安全逻辑
- **WHEN** 公共输入层更新零速度或超时保护
- **THEN** 两个 flavor 必须通过同一实现获得该修复

### Requirement: 巡检版只增加模式切换
general 版 MUST 保留基础遥控能力并显示 AUTO/MANUAL 切换，MUST NOT 创建 NX `33446` 连接或显示建图、地图、定位、导航和任务入口。

#### Scenario: 打开巡检版主屏
- **WHEN** general App 启动并连接匹配 driver
- **THEN** 主屏显示模式切换和基础遥控控件，不显示任务入口且后台不存在 NX 业务会话

### Requirement: SAR 版模式必须由 NX 主控管理
SAR 版 MUST NOT 显示 AppMode 切换或直接发送 `SET_APP_MODE`，并 SHALL 保留 NX 业务连接与建图导航功能。

#### Scenario: SAR 自动导航期间返回人工控制
- **WHEN** 操作者在 SAR App 取消导航
- **THEN** App 必须向 NX 发送取消业务请求，由 NX 完成停车和 MANUAL 交接

### Requirement: 驱动业务连接必须等待产品准入
两个 flavor MUST 使用各自固定的 `ProductType`，只有收到匹配协议、匹配产品且 `admitted=true` 的服务端心跳后才能显示驱动已连接或发送业务命令。

#### Scenario: general App 连接 SAR driver
- **WHEN** general App 连接配置为 SAR 产品的 driver
- **THEN** App 必须显示产品不匹配并停止订阅与命令输出
