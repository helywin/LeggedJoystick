package com.helywin.leggedjoystick.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.OperationMode

class WorkflowSwitchPlannerTest {
    @Test
    fun localizationToMappingStopsRuntimeOnceAndWaitsForStandby() {
        val request = WorkflowSwitchRequest(
            target = TaskWorkspace.MAPPING,
            sessionGeneration = 7L
        )
        val authority = authority(
            mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
            allowed = setOf(ActionCode.ACTION_CODE_STOP_RUNTIME)
        )

        assertEquals(
            WorkflowSwitchPlan.Execute(WorkflowSwitchCommand.STOP_RUNTIME),
            WorkflowSwitchPlanner.plan(request, authority, dispatched = null)
        )
        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                authority,
                dispatched = WorkflowSwitchCommand.STOP_RUNTIME
            ) is WorkflowSwitchPlan.Wait
        )
        assertEquals(
            WorkflowSwitchPlan.Complete,
            WorkflowSwitchPlanner.plan(
                request,
                authority.copy(mode = OperationMode.OPERATION_MODE_STANDBY),
                dispatched = WorkflowSwitchCommand.STOP_RUNTIME
            )
        )
    }

    @Test
    fun mappingSaveSwitchFollowsFinishReviewSaveStandby() {
        val request = WorkflowSwitchRequest(
            target = TaskWorkspace.NAVIGATION,
            sessionGeneration = 7L,
            mappingExitChoice = MappingExitChoice.SAVE,
            mapDisplayName = "map-20260821"
        )
        val running = authority(
            mode = OperationMode.OPERATION_MODE_MAPPING_RUNNING,
            allowed = setOf(ActionCode.ACTION_CODE_FINISH_MAPPING)
        )

        assertEquals(
            WorkflowSwitchPlan.Execute(WorkflowSwitchCommand.FINISH_MAPPING),
            WorkflowSwitchPlanner.plan(request, running, dispatched = null)
        )
        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                running,
                dispatched = WorkflowSwitchCommand.FINISH_MAPPING
            ) is WorkflowSwitchPlan.Wait
        )

        val review = authority(
            mode = OperationMode.OPERATION_MODE_MAPPING_REVIEW,
            allowed = setOf(ActionCode.ACTION_CODE_SAVE_MAP)
        )
        assertEquals(
            WorkflowSwitchPlan.Execute(WorkflowSwitchCommand.SAVE_MAP),
            WorkflowSwitchPlanner.plan(
                request,
                review,
                dispatched = WorkflowSwitchCommand.FINISH_MAPPING
            )
        )
        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                review,
                dispatched = WorkflowSwitchCommand.SAVE_MAP
            ) is WorkflowSwitchPlan.Wait
        )
        assertEquals(
            WorkflowSwitchPlan.Complete,
            WorkflowSwitchPlanner.plan(
                request,
                authority(mode = OperationMode.OPERATION_MODE_STANDBY),
                dispatched = WorkflowSwitchCommand.SAVE_MAP
            )
        )
    }

    @Test
    fun mappingDiscardSwitchUsesDiscardAfterReview() {
        val request = WorkflowSwitchRequest(
            target = TaskWorkspace.NAVIGATION,
            sessionGeneration = 4L,
            mappingExitChoice = MappingExitChoice.DISCARD
        )
        val review = authority(
            generation = 4L,
            mode = OperationMode.OPERATION_MODE_MAPPING_REVIEW,
            allowed = setOf(ActionCode.ACTION_CODE_DISCARD_MAP)
        )

        assertEquals(
            WorkflowSwitchPlan.Execute(WorkflowSwitchCommand.DISCARD_MAP),
            WorkflowSwitchPlanner.plan(request, review, dispatched = null)
        )
    }

    @Test
    fun connectionOrSessionChangeStopsOldSwitchIntent() {
        val request = WorkflowSwitchRequest(
            target = TaskWorkspace.MAPPING,
            sessionGeneration = 3L
        )

        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                authority(generation = 4L, mode = OperationMode.OPERATION_MODE_STANDBY),
                dispatched = null
            ) is WorkflowSwitchPlan.Failed
        )
        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                authority(
                    generation = 3L,
                    connected = false,
                    mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING
                ),
                dispatched = null
            ) is WorkflowSwitchPlan.Failed
        )
    }

    @Test
    fun mappingPreparationAndSavingWaitInsteadOfSendingConflictingCommands() {
        val request = WorkflowSwitchRequest(
            target = TaskWorkspace.NAVIGATION,
            sessionGeneration = 7L,
            mappingExitChoice = MappingExitChoice.DISCARD
        )

        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                authority(mode = OperationMode.OPERATION_MODE_MAPPING_PREPARING),
                dispatched = null
            ) is WorkflowSwitchPlan.Wait
        )
        assertTrue(
            WorkflowSwitchPlanner.plan(
                request,
                authority(mode = OperationMode.OPERATION_MODE_MAPPING_SAVING),
                dispatched = null
            ) is WorkflowSwitchPlan.Wait
        )
    }

    private fun authority(
        generation: Long = 7L,
        connected: Boolean = true,
        mode: OperationMode,
        allowed: Set<ActionCode> = emptySet(),
        requestInFlight: Boolean = false
    ) = WorkflowSwitchAuthority(
        sessionGeneration = generation,
        connected = connected,
        mode = mode,
        allowedActions = allowed,
        requestInFlight = requestInFlight
    )
}
