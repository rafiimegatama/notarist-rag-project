package com.notarist.kase.infrastructure.persistence.mapper;

import com.notarist.kase.domain.model.Timeline;
import com.notarist.kase.domain.model.TimelineEntry;
import com.notarist.kase.domain.model.TimelineEntryType;
import com.notarist.kase.domain.state.TimelineStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.Role;
import com.notarist.kase.domain.valueobject.TimelineEntryId;
import com.notarist.kase.domain.valueobject.TimelineId;
import com.notarist.kase.infrastructure.persistence.postgres.TimelineEntryJpaEntity;
import com.notarist.kase.infrastructure.persistence.postgres.TimelineJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Translates between the {@link Timeline} aggregate and its JPA rows. */
@Component
public class TimelineMapper {

    public Timeline toDomain(TimelineJpaEntity e) {
        List<TimelineEntry> entries = e.getEntries().stream()
                .map(this::entryToDomain)
                .toList();

        return Timeline.rehydrate(
                TimelineId.of(UUID.fromString(e.getTimelineId())),
                CaseId.of(UUID.fromString(e.getCaseId())),
                UUID.fromString(e.getTenantId()),
                TimelineStatus.valueOf(e.getStatus()),
                entries,
                e.getCreatedAt());
    }

    private TimelineEntry entryToDomain(TimelineEntryJpaEntity e) {
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

    /** A fresh timeline row plus all of its entries — the INSERT path for a brand-new timeline. */
    public TimelineJpaEntity toNewEntity(Timeline t) {
        TimelineJpaEntity entity = new TimelineJpaEntity(
                t.timelineId().value().toString(),
                t.caseId().value().toString(),
                t.tenantId().toString(),
                t.status().name(),
                t.createdAt());
        for (TimelineEntry entry : t.entries()) {
            entity.addEntry(entryToEntity(entry));
        }
        return entity;
    }

    public TimelineEntryJpaEntity entryToEntity(TimelineEntry entry) {
        return new TimelineEntryJpaEntity(
                entry.entryId().value().toString(),
                entry.type().name(),
                entry.description(),
                entry.actor().userId().toString(),
                entry.actor().role().name(),
                entry.occurredAt(),
                entry.sequence());
    }
}
