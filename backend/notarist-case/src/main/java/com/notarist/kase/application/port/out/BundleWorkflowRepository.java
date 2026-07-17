package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.BundleWorkflow;
import com.notarist.kase.domain.valueobject.BundleId;

import java.util.Optional;

/** Persistence port for the {@link BundleWorkflow} process aggregate. */
public interface BundleWorkflowRepository {

    void save(BundleWorkflow workflow);

    Optional<BundleWorkflow> findById(BundleId bundleId);
}
