package com.notarist.verification.infrastructure.persistence.mapper;

import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.ItemAuditEntry;
import com.notarist.verification.domain.model.Verification;
import com.notarist.verification.domain.state.ChecklistItemStatus;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.VerificationId;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationChecklistItemJpaEntity;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationItemAuditJpaEntity;
import com.notarist.verification.infrastructure.persistence.postgres.VerificationJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Translates between the {@link Verification} aggregate and its JPA rows. No business logic here. */
@Component
public class VerificationMapper {

    // ---- entity → domain -----------------------------------------------------------------------

    public Verification toDomain(VerificationJpaEntity e) {
        List<ChecklistItem> items = e.getItems().stream().map(this::toDomainItem).toList();
        return Verification.rehydrate(
                VerificationId.of(UUID.fromString(e.getVerificationId())),
                UUID.fromString(e.getBundleId()),
                UUID.fromString(e.getTenantId()),
                VerificationStatus.valueOf(e.getStatus()),
                e.getReviewerId() != null ? UUID.fromString(e.getReviewerId()) : null,
                e.getReviewedAt(),
                e.getCreatedAt(),
                items,
                e.getLastAuditSequence());
    }

    private ChecklistItem toDomainItem(VerificationChecklistItemJpaEntity i) {
        return ChecklistItem.rehydrate(
                ItemId.of(UUID.fromString(i.getItemId())),
                ChecklistCategory.valueOf(i.getCategory()),
                i.getTitle(),
                i.isMandatory(),
                CheckType.valueOf(i.getCheckType()),
                i.getSortOrder(),
                ChecklistItemStatus.valueOf(i.getStatus()),
                i.getDecision() != null ? Decision.valueOf(i.getDecision()) : null,
                i.getReviewerId() != null ? UUID.fromString(i.getReviewerId()) : null,
                i.getReviewedAt(),
                i.getComment());
    }

    // ---- domain → entity (INSERT path) ---------------------------------------------------------

    public VerificationJpaEntity toNewEntity(Verification v) {
        VerificationJpaEntity entity = new VerificationJpaEntity(
                v.verificationId().value().toString(),
                v.bundleId().toString(),
                v.tenantId().toString(),
                v.status().name(),
                v.reviewerId() != null ? v.reviewerId().toString() : null,
                v.reviewedAt(),
                v.lastAuditSequence(),
                v.createdAt());
        for (ChecklistItem i : v.items()) {
            entity.addItem(toNewItemEntity(i));
        }
        return entity;
    }

    private VerificationChecklistItemJpaEntity toNewItemEntity(ChecklistItem i) {
        return new VerificationChecklistItemJpaEntity(
                i.itemId().value().toString(),
                i.category().name(),
                i.title(),
                i.mandatory(),
                i.checkType().name(),
                i.status().name(),
                i.decision() != null ? i.decision().name() : null,
                i.reviewerId() != null ? i.reviewerId().toString() : null,
                i.reviewedAt(),
                i.comment(),
                i.sortOrder());
    }

    // ---- domain → entity (UPDATE path) ---------------------------------------------------------

    public void copyMutableState(Verification v, VerificationJpaEntity managed) {
        managed.setStatus(v.status().name());
        managed.setReviewerId(v.reviewerId() != null ? v.reviewerId().toString() : null);
        managed.setReviewedAt(v.reviewedAt());
        managed.setLastAuditSequence(v.lastAuditSequence());

        Map<String, VerificationChecklistItemJpaEntity> managedItems = managed.getItems().stream()
                .collect(Collectors.toMap(VerificationChecklistItemJpaEntity::getItemId, Function.identity()));
        for (ChecklistItem i : v.items()) {
            VerificationChecklistItemJpaEntity mi = managedItems.get(i.itemId().value().toString());
            if (mi == null) continue;   // items are fixed after provisioning; nothing to add/remove
            mi.setStatus(i.status().name());
            mi.setDecision(i.decision() != null ? i.decision().name() : null);
            mi.setReviewerId(i.reviewerId() != null ? i.reviewerId().toString() : null);
            mi.setReviewedAt(i.reviewedAt());
            mi.setComment(i.comment());
        }
    }

    // ---- audit ---------------------------------------------------------------------------------

    public VerificationItemAuditJpaEntity toAuditEntity(String verificationId, ItemAuditEntry a) {
        return new VerificationItemAuditJpaEntity(
                a.auditId().toString(),
                verificationId,
                a.itemId().value().toString(),
                a.decision().name(),
                a.previousDecision() != null ? a.previousDecision().name() : null,
                a.comment(),
                a.reviewerId().toString(),
                a.reviewerRole(),
                a.occurredAt(),
                a.sequence());
    }
}
