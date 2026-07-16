package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.valueobject.BundleId;

import java.util.Optional;

/** Persistence port for the bundle's append-only timeline. */
public interface BundleTimelineRepository {

    void save(BundleTimeline timeline);

    Optional<BundleTimeline> findByBundle(BundleId bundleId);
}
