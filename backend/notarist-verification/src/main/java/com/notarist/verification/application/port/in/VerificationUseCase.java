package com.notarist.verification.application.port.in;

import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.api.response.VerificationSummaryResponse;
import com.notarist.verification.application.command.ChangeVerificationStatusCommand;
import com.notarist.verification.application.command.UpdateChecklistItemCommand;
import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.valueobject.BundleId;

/** The verification use cases behind the four REST endpoints. */
public interface VerificationUseCase {

    /** The full verification payload: status, progress, checklist, categories, summary. */
    VerificationResponse getVerification(BundleId bundleId, CallerContext caller);

    /** Progress-only view. */
    VerificationSummaryResponse getSummary(BundleId bundleId, CallerContext caller);

    /** Record a decision on one checklist item. Returns the refreshed verification. */
    VerificationResponse updateChecklistItem(UpdateChecklistItemCommand command);

    /** Move the verification through its lifecycle. Legality is decided by the aggregate. */
    VerificationResponse changeStatus(ChangeVerificationStatusCommand command);
}
