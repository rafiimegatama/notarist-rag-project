package com.notarist.ingest.domain.service;

import com.notarist.ingest.domain.model.PipelineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineStateMachineTest {

    /**
     * Drives the state machine exactly the way PipelineCoordinator does — for each dequeued
     * stage: transition to completedStageFor(stage), then ask nextPendingStage(newStatus) for
     * the row to enqueue — and asserts the walk reaches COMPLETED.
     *
     * <p>Regression guard: nextPendingStage previously had no INDEX_PENDING case, so the walk
     * dead-ended after EMBED and the INDEX stage was never enqueued (chunks never reached Qdrant).
     */
    @Test
    @DisplayName("a job walks UPLOADED → OCR → NER → CHUNK → EMBED → INDEX → COMPLETED without stalling")
    void fullPipelineReachesCompleted() {
        List<PipelineStatus> stagesRun = new ArrayList<>();

        PipelineStatus status = PipelineStatus.UPLOADED;
        Optional<PipelineStatus> queued = PipelineStateMachine.nextPendingStage(status);

        for (int guard = 0; queued.isPresent() && guard < 10; guard++) {
            PipelineStatus stage = queued.get();
            stagesRun.add(stage);

            status = PipelineStateMachine.completedStageFor(stage);
            assertTrue(PipelineStateMachine.isValidTransition(stage, status),
                    "coordinator would reject its own transition " + stage + " → " + status);

            queued = PipelineStateMachine.nextPendingStage(status);
        }

        assertEquals(
                List.of(PipelineStatus.OCR_PENDING, PipelineStatus.NER_PENDING,
                        PipelineStatus.CHUNK_PENDING, PipelineStatus.EMBED_PENDING,
                        PipelineStatus.INDEX_PENDING),
                stagesRun,
                "every stage must be enqueued exactly once, in order");
        assertEquals(PipelineStatus.COMPLETED, status, "pipeline stalled at " + status);
    }

    @Test
    @DisplayName("embedding completion enqueues the INDEX stage")
    void embedCompletionEnqueuesIndex() {
        PipelineStatus afterEmbed = PipelineStateMachine.completedStageFor(PipelineStatus.EMBED_PENDING);

        assertEquals(PipelineStatus.INDEX_PENDING, afterEmbed);
        assertEquals(Optional.of(PipelineStatus.INDEX_PENDING),
                PipelineStateMachine.nextPendingStage(afterEmbed));
    }

    @Test
    @DisplayName("COMPLETED is terminal — nothing further is enqueued")
    void completedEnqueuesNothing() {
        assertEquals(PipelineStatus.COMPLETED,
                PipelineStateMachine.completedStageFor(PipelineStatus.INDEX_PENDING));
        assertFalse(PipelineStateMachine.nextPendingStage(PipelineStatus.COMPLETED).isPresent());
    }

    @Test
    @DisplayName("every stage the machine enqueues has a completion status (no unroutable stage)")
    void everyEnqueueableStageIsRoutable() {
        for (PipelineStatus status : PipelineStatus.values()) {
            PipelineStateMachine.nextPendingStage(status).ifPresent(stage ->
                    assertTrue(PipelineStateMachine.isValidTransition(
                                    stage, PipelineStateMachine.completedStageFor(stage)),
                            "stage " + stage + " is enqueued but cannot complete"));
        }
    }
}
