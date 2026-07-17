package com.notarist.kase.application.port.out;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.List;
import java.util.Optional;

public interface BundleRepository {

    void save(Bundle bundle);

    Optional<Bundle> findById(BundleId bundleId);

    List<Bundle> findByCase(CaseId caseId);

    /**
     * The bundle that holds the given document, if any. Used to route an ingestion outcome back to its
     * case without the pipeline ever having to carry a caseId. RLS scopes the result to the caller's
     * tenant; if a document somehow appears in more than one bundle, the most recently created wins.
     */
    Optional<Bundle> findByDocumentId(DocumentId documentId);
}
