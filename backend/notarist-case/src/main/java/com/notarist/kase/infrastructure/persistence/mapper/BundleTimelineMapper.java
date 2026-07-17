package com.notarist.kase.infrastructure.persistence.mapper;

import com.notarist.kase.domain.model.BundleTimeline;
import com.notarist.kase.domain.model.TimelineEntry;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.TimelineStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.Role;
import com.notarist.kase.domain.valueobject.TimelineEntryId;
import com.notarist.kase.infrastructure.persistence.postgres.BundleTimelineEntryJpaEntity;
import com.notarist.kase.infrastructure.persistence.postgres.BundleTimelineJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Translates between the {@link BundleTimeline} aggregate and its JPA rows. */
@Component
public class BundleTimelineMapper {

    public BundleTimeline toDomain(BundleTimelineJpaEntity e) {
        List<TimelineEntry> entries = e.getEntries().stream().map(this::entryToDomain).toList();
        return BundleTimeline.rehydrate(
                UUID.fromString(e.getTimelineId()),
                BundleId.of(UUID.fromString(e.getBundleId())),
                UUID.fromString(e.getTenantId()),
                TimelineStatus.valueOf(e.getStatus()),
                entries,
                e.getCreatedAt());
    }

    private TimelineEntry entryToDomain(BundleTimelineEntryJpaEntity e) {
        Role role = Role.valueOf(e.getActorRole());
        Actor actor = role == Role.SYSTEM
                ? Actor.system()
                : Actor.of(UUID.fromString(e.getActorUserId()), role);
        return new TimelineEntry(
                TimelineEntryId.of(UUID.fromString(e.getEntryId())),
                TimelineEntryType.valueOf(e.getEntryType()),
                e.getDescription(),
                actor,
                e.getOccurredAt(),
                e.getSequence());
    }

    public BundleTimelineJpaEntity toNewEntity(BundleTimeline t) {
        BundleTimelineJpaEntity entity = new BundleTimelineJpaEntity(
                t.timelineId().toString(),
                t.bundleId().value().toString(),
                t.tenantId().toString(),
                t.status().name(),
                t.createdAt());
        for (TimelineEntry entry : t.entries()) {
            entity.addEntry(entryToEntity(entry));
        }
        return entity;
    }

    public BundleTimelineEntryJpaEntity entryToEntity(TimelineEntry entry) {
        return new BundleTimelineEntryJpaEntity(
                entry.entryId().value().toString(),
                entry.type().name(),
                entry.description(),
                entry.actor().userId().toString(),
                entry.actor().role().name(),
                entry.occurredAt(),
                entry.sequence());
    }
}
