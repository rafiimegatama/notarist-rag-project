package com.notarist.kase.infrastructure.persistence.postgres;

import com.notarist.kase.application.port.out.TimelineRepository;
import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntry;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.TimelineId;
import com.notarist.kase.infrastructure.persistence.mapper.TimelineMapper;
import com.notarist.kase.infrastructure.security.RlsContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Timeline persistence. Append-only in practice: on update we only ever ADD the entries the
 * in-memory aggregate has that the row does not yet, and update the sealed/active status. We never
 * mutate or delete an existing entry — that is the whole point of the aggregate, enforced here by
 * only inserting entries whose id is new. The unique (timeline_id, sequence) constraint is the
 * last-line guard against two writers appending the same position.
 */
@Repository
@Transactional
public class TimelineRepositoryImpl implements TimelineRepository {

    private final TimelineJpaRepository jpaRepository;
    private final TimelineMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public TimelineRepositoryImpl(TimelineJpaRepository jpaRepository, TimelineMapper mapper,
                                  RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    @Override
    public void save(Timeline timeline) {
        rlsContextApplier.applyIfPresent(entityManager);
        Optional<TimelineJpaEntity> existing =
                jpaRepository.findById(timeline.timelineId().value().toString());

        if (existing.isEmpty()) {
            jpaRepository.save(mapper.toNewEntity(timeline));
            return;
        }

        TimelineJpaEntity managed = existing.get();
        managed.setStatus(timeline.status().name());

        Set<String> persistedEntryIds = managed.getEntries().stream()
                .map(TimelineEntryJpaEntity::getEntryId)
                .collect(Collectors.toSet());

        for (TimelineEntry entry : timeline.entries()) {
            String entryId = entry.entryId().value().toString();
            if (!persistedEntryIds.contains(entryId)) {
                managed.addEntry(mapper.entryToEntity(entry));
            }
        }
        jpaRepository.save(managed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Timeline> findById(TimelineId timelineId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(timelineId.value().toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Timeline> findByCase(CaseId caseId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByCaseId(caseId.value().toString()).map(mapper::toDomain);
    }
}
