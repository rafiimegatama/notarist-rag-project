package com.notarist.kase.domain.factory;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/**
 * Creates a Case in the only legal birth state, CASE_CREATED, together with the Timeline that will
 * record its story.
 *
 * <p>The factory exists because a valid Case is more than a constructed object: it must start in the
 * right state, and it must have somewhere to record what happens to it. Leaving that to each caller is
 * how you end up with cases that have no history.
 */
public final class CaseFactory {

    private CaseFactory() {}

    /** A Case and its Timeline, created together and consistent from birth. */
    public record NewCase(Case aCase, Timeline timeline) {}

    public static NewCase create(CaseNumber caseNumber, CaseType caseType, UUID tenantId,
                                 Actor actor, UUID assignedNotarisId,
                                 CorrelationId correlationId, TraceId traceId) {

        CaseId caseId = CaseId.generate();

        Case aCase = Case.open(caseId, caseNumber, caseType, tenantId, actor, assignedNotarisId,
                correlationId, traceId);

        Timeline timeline = Timeline.start(TimelineId.generate(), caseId, tenantId);
        timeline.append(TimelineEntryType.CASE_OPENED,
                "Case " + caseNumber + " dibuka (" + caseType + ")",
                actor, correlationId, traceId);

        return new NewCase(aCase, timeline);
    }
}
