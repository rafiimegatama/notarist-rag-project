package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.TimelineId;

import java.util.Optional;

public interface TimelineRepository {

    void save(Timeline timeline);

    Optional<Timeline> findById(TimelineId timelineId);

    Optional<Timeline> findByCase(CaseId caseId);
}
