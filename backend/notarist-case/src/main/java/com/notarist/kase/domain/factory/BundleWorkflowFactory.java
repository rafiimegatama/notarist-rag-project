package com.notarist.kase.domain.factory;

import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.UUID;

/**
 * Creates a bundle's process workflow together with the timeline that records it — the same
 * "born consistent, with somewhere to record its story" contract as {@code CaseFactory}.
 */
public final class BundleWorkflowFactory {

    private BundleWorkflowFactory() {}

    public record NewBundleWorkflow(BundleWorkflow workflow, BundleTimeline timeline) {}

    public static NewBundleWorkflow create(BundleId bundleId, CaseId caseId, UUID tenantId, Actor actor) {
        BundleWorkflow workflow = BundleWorkflow.start(bundleId, caseId, tenantId);

        BundleTimeline timeline = BundleTimeline.start(UUID.randomUUID(), bundleId, tenantId);
        timeline.append(TimelineEntryType.CASE_OPENED, "Bundle dibuka", actor);

        return new NewBundleWorkflow(workflow, timeline);
    }
}
