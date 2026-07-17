package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.kase.application.port.out.BundleTimelineRepository;
import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.model.TimelineEntry;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.infrastructure.persistence.mapper.BundleTimelineMapper;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bundle timeline persistence. Append-only in practice: on update it only ADDS the entries the
 * in-memory aggregate has that the row does not, and updates the sealed/active status. The unique
 * (timeline_id, sequence) constraint is the last-line guard against a duplicate append.
 */
@Repository
@Transactional
public class BundleTimelineRepositoryImpl implements BundleTimelineRepository {

    private final BundleTimelineJpaRepository jpaRepository;
    private final BundleTimelineMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public BundleTimelineRepositoryImpl(BundleTimelineJpaRepository jpaRepository,
                                        BundleTimelineMapper mapper,
                                        RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(BundleTimeline timeline) {
        rlsContextApplier.applyIfPresent(entityManager);
        Optional<BundleTimelineJpaEntity> existing =
                jpaRepository.findById(timeline.timelineId().toString());

        if (existing.isEmpty()) {
            jpaRepository.save(mapper.toNewEntity(timeline));
            return;
        }

        BundleTimelineJpaEntity managed = existing.get();
        managed.setStatus(timeline.status().name());

        Set<String> persisted = managed.getEntries().stream()
                .map(BundleTimelineEntryJpaEntity::getEntryId)
                .collect(Collectors.toSet());

        for (TimelineEntry entry : timeline.entries()) {
            if (!persisted.contains(entry.entryId().value().toString())) {
                managed.addEntry(mapper.entryToEntity(entry));
            }
        }
        jpaRepository.save(managed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BundleTimeline> findByBundle(BundleId bundleId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByBundleId(bundleId.value().toString()).map(mapper::toDomain);
    }
}
