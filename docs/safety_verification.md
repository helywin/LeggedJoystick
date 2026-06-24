# 安全保护验证记录

## 验证边界

本记录覆盖 App 侧可以主动处理的停止输出路径。进程被系统或用户强杀时，App 没有机会执行 Kotlin 清理逻辑，停止动作依赖 `legged_driver` 的移动命令超时保护；App 侧负责在可执行的生命周期和回调里尽早停止连续运动输出。

## 已覆盖场景

| 场景 | App 侧处理 | 验证方式 |
| --- | --- | --- |
| 页面进入后台 | `MainActivity.onPause()` 调用 `controller.pauseMovementOutput()` | `SafetyContractTest.lifecycleAndConnectionFailuresStopMovementOutput` |
| 主流程销毁 | `MainActivity.onDestroy()` 调用 `controller.cleanup()` | `SafetyContractTest.lifecycleAndConnectionFailuresStopMovementOutput` |
| 用户断开连接 | `disconnect()` 调用 `stopVelocityLoop()` 和 `stopHeadControl()` | `SafetyContractTest.lifecycleAndConnectionFailuresStopMovementOutput` |
| ZMQ 断网或连接失败 | `handleConnectionState()` 在非连接状态调用 `stopVelocityLoop()` 并清空控制权 | `SafetyContractTest.inputTimeoutAndControlLossClearMovementOutput` |
| 外部摇杆输入超时 | `RemoteInputStatus.TIMEOUT` 清零 `currentMovementIntent` 和调试快照 | `SafetyContractTest.inputTimeoutAndControlLossClearMovementOutput` |
| 控制权丢失 | `MESSAGE_TYPE_CONTROL_LOST` 调用 `stopVelocityLoop()`、`stopHeadControl()` 并标记 `LOST` | `SafetyContractTest.inputTimeoutAndControlLossClearMovementOutput` |
| 已发送移动后停止循环 | `stopVelocityLoop()` 在 `lastCommandSent` 为真时发送 `0f, 0f, 0f` 并复位状态 | `SafetyContractTest.stopVelocityLoopSendsZeroWhenMovementWasActive` |

## 前台服务设备冒烟

验证设备：`Standard-10inch_A2`，Android 13，ADB 序列号 `d`。

验证步骤：

1. 开启工程 Mock 模式，进入主控页并点击连接。
2. 通过 UI 树确认主屏进入已连接状态，输入源为工程 Mock，电量和状态浮层正常显示。
3. 抓取主屏截图：`docs/assets/implementation/foreground-service-device-retry-20260624.png`。
4. 按 Home 将 App 切到后台，使用 `dumpsys activity services com.helywin.leggedjoystick/.service.RemoteControlForegroundService` 检查服务状态。
5. 使用 `dumpsys notification --noredact` 检查常驻通知和通知通道。

验证结果：

| 项目 | 结果 |
| --- | --- |
| 主屏连接状态 | `工程 Mock`、`已连接`、`接收中` |
| 前台服务 | `isForeground=true`，`foregroundId=1001` |
| 通知通道 | `remote_control_connection`，名称为“机器狗遥控链路” |
| 后台进程 | 按 Home 后 `pidof com.helywin.leggedjoystick` 仍返回进程 ID |
| 右侧系统导航栏 | 主控内容宽度为 1812px，设备横屏物理宽度为 1920px，右侧导航区域未被完全覆盖 |

## 当前结论

App 侧后台、断连、输入超时、控制权丢失和主动清理路径都有停止输出保护。突然杀进程无法保证 App 发送最后一帧零速度，因此该场景以 `driver` 超时保护兜底；这与当前 OpenSpec 的“停止速度输出让 driver 侧超时保护接管”一致。
