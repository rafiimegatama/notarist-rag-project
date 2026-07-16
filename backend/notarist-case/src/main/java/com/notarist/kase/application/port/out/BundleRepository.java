package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.util.List;
import java.util.Optional;

public interface BundleRepository {

    void save(Bundle bundle);

    Optional<Bundle> findById(BundleId bundleId);

    List<Bundle> findByCase(CaseId caseId);
}
