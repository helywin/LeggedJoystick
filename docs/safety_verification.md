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

## 当前结论

App 侧后台、断连、输入超时、控制权丢失和主动清理路径都有停止输出保护。突然杀进程无法保证 App 发送最后一帧零速度，因此该场景以 `driver` 超时保护兜底；这与当前 OpenSpec 的“停止速度输出让 driver 侧超时保护接管”一致。
