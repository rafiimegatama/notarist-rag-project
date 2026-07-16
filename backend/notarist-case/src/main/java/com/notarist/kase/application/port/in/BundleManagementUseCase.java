package com.notarist.kase.application.port.in;

import com.notarist.kase.api.response.BundleResponse;
import com.notarist.kase.api.response.BundleTimelineResponse;
import com.notarist.kase.application.command.ChangeBundleStatusCommand;
import com.notarist.kase.application.command.OpenBundleCommand;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.List;

/** The inbound port for the Bundle bounded context — everything the REST layer may ask of it. */
public interface BundleManagementUseCase {

    BundleResponse openBundle(OpenBundleCommand command);

    List<BundleResponse> listBundles(CaseId caseId, CallerContext caller);

    BundleResponse getBundle(BundleId bundleId, CallerContext caller);

    BundleResponse changeStatus(ChangeBundleStatusCommand command);

    BundleTimelineResponse getTimeline(BundleId bundleId, CallerContext caller);
}
