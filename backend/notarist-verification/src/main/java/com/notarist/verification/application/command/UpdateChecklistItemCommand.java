package com.notarist.verification.application.command;

import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.ItemId;

/** Update one checklist item. {@code comment} is mandatory for a {@code FAIL} decision. */
public record UpdateChecklistItemCommand(
        BundleId bundleId,
        ItemId itemId,
        Decision decision,
        String comment,
        CallerContext caller
) {
    public UpdateChecklistItemCommand {
        if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (decision == null) throw new IllegalArgumentException("decision is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
