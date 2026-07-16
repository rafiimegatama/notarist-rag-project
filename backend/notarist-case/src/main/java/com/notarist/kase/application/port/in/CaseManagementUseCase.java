package com.notarist.kase.application.port.in;

import com.notarist.core.api.response.PageResponse;
import com.notarist.kase.api.response.ActivityResponse;
import com.notarist.kase.api.response.CaseResponse;
import com.notarist.kase.api.response.TimelineResponse;
import com.notarist.kase.application.command.ChangeCaseStatusCommand;
import com.notarist.kase.application.command.OpenCaseCommand;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.query.CaseFilter;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.List;

/**
 * The inbound port for the Case module — everything the REST layer may ask of it. The controller
 * depends on this interface, never on the concrete service, so the transport and the use cases stay
 * separable.
 */
public interface CaseManagementUseCase {

    CaseResponse openCase(OpenCaseCommand command);

    CaseResponse changeStatus(ChangeCaseStatusCommand command);

    CaseResponse getCase(CaseId caseId, CallerContext caller);

    PageResponse<CaseResponse> listCases(CaseFilter filter, int page, int size, CallerContext caller);

    TimelineResponse getTimeline(CaseId caseId, CallerContext caller);

    /** The case's activity feed — its timeline entries projected as activities, newest first. */
    List<ActivityResponse> getActivities(CaseId caseId, CallerContext caller);
}
