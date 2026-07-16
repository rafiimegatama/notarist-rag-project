package com.notarist.kase.domain.factory;

import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.TimelineId;

import java.util.UUID;

/** A Timeline is normally created by {@link CaseFactory}; this is for rebuilding one explicitly. */
public final class TimelineFactory {

    private TimelineFactory() {}

    public static Timeline createFor(CaseId caseId, UUID tenantId) {
        return Timeline.start(TimelineId.generate(), caseId, tenantId);
    }
}
