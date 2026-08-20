package com.helywin.leggedjoystick.ui.mapping

import com.helywin.leggedjoystick.mapping.MapIdentityModel
import com.helywin.leggedjoystick.mapping.MapControlOwner
import com.helywin.leggedjoystick.mapping.MapNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.AllowedAction
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.StateSnapshot

class NavigationWorkflowPresenterTest {
    @Test
    fun waitingInitialPoseUsesNeutralWorkflowLocalizationLabel() {
        val snapshot = StateSnapshot(
            operation_mode = OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE
        )

        val presentation = NavigationWorkflowPresenter.present(
            snapshot,
            MapNavigationState()
        )

        assertEquals("待初始位姿", presentation.localizationLabel)
        assertEquals(NavigationStatusTone.NEUTRAL, presentation.localizationTone)
    }

    @Test
    fun standbySelectedMapUsesSwitchMapAndIgnoresOtherActionBlockers() {
        val snapshot = StateSnapshot(
            operation_mode = OperationMode.OPERATION_MODE_STANDBY,
            allowed_actions = listOf(
                AllowedAction(
                    action = ActionCode.ACTION_CODE_SWITCH_MAP,
                    allowed = true
                ),
                AllowedAction(
                    action = ActionCode.ACTION_CODE_FINISH_MAPPING,
                    allowed = false,
                    blocking_reasons = listOf("mapping_not_running")
                ),
                AllowedAction(
                    action = ActionCode.ACTION_CODE_EXIT_MAINTENANCE,
                    allowed = false,
                    blocking_reasons = listOf("not_in_maintenance")
                )
            )
        )
        val navigation = MapNavigationState(
            selectedMap = MapIdentityModel("map-a", 2L)
        )

        val presentation = NavigationWorkflowPresenter.present(snapshot, navigation)

        assertEquals(NavigationWorkflowStep.LOAD_MAP, presentation.step)
        assertEquals(ActionCode.ACTION_CODE_SWITCH_MAP, presentation.primaryAction)
        assertEquals("下一步：加载选中地图", presentation.stageText)
        assertEquals("未启动", presentation.localizationLabel)
        assertEquals(NavigationStatusTone.NEUTRAL, presentation.localizationTone)
        assertEquals(true, presentation.actionAllowed)
        assertTrue(presentation.blockingReasons.isEmpty())
    }

    @Test
    fun currentActionBlockerIsKeptAndTranslatedForOperator() {
        val snapshot = StateSnapshot(
            operation_mode = OperationMode.OPERATION_MODE_STANDBY,
            allowed_actions = listOf(
                AllowedAction(
                    action = ActionCode.ACTION_CODE_SWITCH_MAP,
                    allowed = false,
                    blocking_reasons = listOf("system_clock:system_clock_set_failed")
                ),
                AllowedAction(
                    action = ActionCode.ACTION_CODE_SAVE_MAP,
                    allowed = false,
                    blocking_reasons = listOf("mapping_not_in_review")
                )
            )
        )
        val navigation = MapNavigationState(
            selectedMap = MapIdentityModel("map-a", 2L)
        )

        val presentation = NavigationWorkflowPresenter.present(snapshot, navigation)

        assertEquals(false, presentation.actionAllowed)
        assertEquals(1, presentation.blockingReasons.size)
        assertEquals("NX 系统时间未就绪", presentation.blockingReasons.single().message)
        assertEquals(
            "system_clock:system_clock_set_failed",
            presentation.blockingReasons.single().code
        )
    }

    @Test
    fun navigatingExposesOnlyCancelNavigationAsTaskAction() {
        val snapshot = StateSnapshot(
            operation_mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
            allowed_actions = listOf(
                AllowedAction(
                    action = ActionCode.ACTION_CODE_CANCEL_NAVIGATION,
                    allowed = true
                )
            )
        )
        val navigation = MapNavigationState(
            currentMap = MapIdentityModel("map-a", 2L),
            controlOwner = MapControlOwner.NAVIGATION_AUTO,
            activeTaskId = "nav-1",
            activeNavigationTaskId = "nav-1"
        )

        val presentation = NavigationWorkflowPresenter.present(snapshot, navigation)

        assertEquals(NavigationWorkflowStep.NAVIGATING, presentation.step)
        assertEquals(ActionCode.ACTION_CODE_CANCEL_NAVIGATION, presentation.primaryAction)
        assertEquals("取消导航", presentation.stepLabel)
        assertEquals(true, presentation.actionAllowed)
    }
}
