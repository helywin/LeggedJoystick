## Why

现有 App 和历史 `LeggedJoystick` 项目不能直接满足新的机器狗遥控目标：UI 需要参考 GenisDog 主屏，协议需要切到 `/home/jiang/code/legged_driver` 当前版本，摇杆输入还要考虑多 App 共用。继续在旧结构上修补会把 UI、输入、厂商 SDK 和协议耦合在一起，后续联调风险高。

## What Changes

- 重写 Android 遥控 App，使用 Kotlin、Jetpack Compose、Timber、Wire、JeroMQ。
- 继续使用当前仓库单 `app` 模块工程壳，并把 Gradle、AGP、Kotlin、AndroidX、Compose、Wire 等依赖升级到当前稳定版。
- 主屏布局参考 GenisDog：全屏视频背景、顶部模式栏、左侧速度三挡、右侧工具列、底部动作组展开/收缩、状态浮层。
- 控制协议以 `legged_driver` 当前 proto 为真源，Android 端作为 ZMQ DEALER 客户端。
- 输入层支持思翼 UniRC UDP 和云卓 G20 RCSDK 两类通道源，并按设备标志自动选择；两类输入统一输出操作者视角的移动意图，当前不做触屏虚拟摇杆，也不实现 Android 广播输入。
- 建立可扩展的遥控器 provider/factory 边界，后续新增机型时不得把设备判断和厂商 SDK 调用堆进控制层。
- 多 App 共用摇杆数据时，不依赖同端口 UDP 复用；优先验证不同客户端端口订阅，必要时使用单采集者分发。
- 保留 GenisDog 抓取素材作为内部参考，正式发布前替换为自有或已授权资产。

## Capabilities

### New Capabilities

- `remote-control-app`: 覆盖新 Kotlin 遥控 App 的主屏 UI、协议接入、输入管线、摇杆共用、安全策略和设置项。

### Modified Capabilities

- 无。

## Impact

- 影响 Android App 主模块、协议生成、ZMQ 传输、输入处理、主屏 UI、设置页、云卓 RCSDK 依赖和联调工具。
- 影响构建脚本、Gradle wrapper、版本目录和 AGP 9 迁移点。
- 现有旧 proto 和 GenisDog 反编译代码不作为新实现基础；云卓 G20 仅通过仓库内已核实的 RCSDK 1.9.2 接入统一输入层。
- `reverse/` 仅保留为本地临时资料，不纳入版本控制。
