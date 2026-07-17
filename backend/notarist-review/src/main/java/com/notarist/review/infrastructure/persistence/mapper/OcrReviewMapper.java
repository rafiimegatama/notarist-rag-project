package com.notarist.review.infrastructure.persistence.mapper;

import com.notarist.review.domain.model.AuthorityItem;
import com.notarist.review.domain.model.FieldAuditEntry;
import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.model.OcrReview;
import com.notarist.review.domain.state.AuthorityDecision;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.AuthorityId;
import com.notarist.review.domain.valueobject.AuthorityType;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.infrastructure.persistence.postgres.OcrAuthorityItemJpaEntity;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewFieldAuditJpaEntity;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewFieldJpaEntity;
import com.notarist.review.infrastructure.persistence.postgres.OcrReviewJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Translates between the {@link OcrReview} aggregate and its JPA rows. No business logic here. */
@Component
public class OcrReviewMapper {

    // ---- entity → domain -----------------------------------------------------------------------

    public OcrReview toDomain(OcrReviewJpaEntity e) {
        List<FieldReview> fields = e.getFields().stream()
                .map(this::toDomainField)
                .toList();
        List<AuthorityItem> authority = e.getAuthorityItems().stream()
                .map(this::toDomainAuthority)
                .toList();

        return OcrReview.rehydrate(
                ReviewId.of(UUID.fromString(e.getReviewId())),
                UUID.fromString(e.getDocumentId()),
                UUID.fromString(e.getTenantId()),
                e.getDocumentName(),
                e.getPageCount(),
                e.isStampDetected(),
                e.isSignatureDetected(),
                e.getOverallConfidence(),
                ReviewStatus.valueOf(e.getReviewStatus()),
                e.getReviewerId() != null ? UUID.fromString(e.getReviewerId()) : null,
                e.getReviewedAt(),
                e.getCreatedAt(),
                fields,
                authority,
                e.getLastAuditSequence());
    }

    private FieldReview toDomainField(OcrReviewFieldJpaEntity f) {
        return FieldReview.rehydrate(
                FieldId.of(UUID.fromString(f.getFieldId())),
                f.getFieldName(),
                f.getDisplayLabel(),
                f.getExtractedValue(),
                f.getCorrectedValue(),
                f.getConfidence(),
                FieldDecision.valueOf(f.getDecision()),
                f.getRejectionReason(),
                f.getReviewerId() != null ? UUID.fromString(f.getReviewerId()) : null,
                f.getReviewedAt(),
                BoundingBox.of(f.getBboxPage(), f.getBboxX(), f.getBboxY(), f.getBboxWidth(), f.getBboxHeight()),
                f.getSortOrder());
    }

    private AuthorityItem toDomainAuthority(OcrAuthorityItemJpaEntity a) {
        return AuthorityItem.rehydrate(
                AuthorityId.of(UUID.fromString(a.getAuthorityId())),
                AuthorityType.valueOf(a.getAuthorityType()),
                a.getRoleLabel(),
                a.getPersonName(),
                a.getContent(),
                a.getConfidence(),
                a.getSortOrder(),
                AuthorityDecision.valueOf(a.getDecision()),
                a.getDecidedAt());
    }

    // ---- domain → entity (INSERT path) ---------------------------------------------------------

    /** A fresh (unmanaged) root with its children attached. Updates go through the managed entity. */
    public OcrReviewJpaEntity toNewEntity(OcrReview r) {
        OcrReviewJpaEntity entity = new OcrReviewJpaEntity(
                r.reviewId().value().toString(),
                r.documentId().toString(),
                r.tenantId().toString(),
                r.status().name(),
                r.reviewerId() != null ? r.reviewerId().toString() : null,
                r.reviewedAt(),
                r.documentName(),
                r.pageCount(),
                r.stampDetected(),
                r.signatureDetected(),
                r.overallConfidence(),
                r.lastAuditSequence(),
                r.createdAt());

        for (FieldReview f : r.fields()) {
            entity.addField(toNewFieldEntity(f));
        }
        for (AuthorityItem a : r.authorityItems()) {
            entity.addAuthorityItem(toNewAuthorityEntity(a));
        }
        return entity;
    }

    private OcrReviewFieldJpaEntity toNewFieldEntity(FieldReview f) {
        BoundingBox b = f.boundingBox();
        return new OcrReviewFieldJpaEntity(
                f.fieldId().value().toString(),
                f.fieldName(),
                f.displayLabel(),
                f.extractedValue(),
                f.correctedValue(),
                f.confidence(),
                f.confidenceLevel().name(),
                f.decision().name(),
                f.rejectionReason(),
                f.reviewerId() != null ? f.reviewerId().toString() : null,
                f.reviewedAt(),
                b.page(), b.x(), b.y(), b.width(), b.height(),
                f.sortOrder());
    }

    private OcrAuthorityItemJpaEntity toNewAuthorityEntity(AuthorityItem a) {
        return new OcrAuthorityItemJpaEntity(
                a.authorityId().value().toString(),
                a.type().name(),
                a.roleLabel(),
                a.personName(),
                a.content(),
                a.confidence(),
                a.decision().name(),
                a.decidedAt(),
                a.sortOrder());
    }

    // ---- domain → entity (UPDATE path) ---------------------------------------------------------

    /** Copies the mutable state of the aggregate onto an already-managed root and its managed children. */
    public void copyMutableState(OcrReview r, OcrReviewJpaEntity managed) {
        managed.setReviewStatus(r.status().name());
        managed.setReviewerId(r.reviewerId() != null ? r.reviewerId().toString() : null);
        managed.setReviewedAt(r.reviewedAt());
        managed.setLastAuditSequence(r.lastAuditSequence());

        Map<String, OcrReviewFieldJpaEntity> managedFields = managed.getFields().stream()
                .collect(Collectors.toMap(OcrReviewFieldJpaEntity::getFieldId, Function.identity()));
        for (FieldReview f : r.fields()) {
            OcrReviewFieldJpaEntity mf = managedFields.get(f.fieldId().value().toString());
            if (mf == null) continue;   // fields are fixed after provisioning; nothing to add/remove
            mf.setDecision(f.decision().name());
            mf.setCorrectedValue(f.correctedValue());
            mf.setRejectionReason(f.rejectionReason());
            mf.setReviewerId(f.reviewerId() != null ? f.reviewerId().toString() : null);
            mf.setReviewedAt(f.reviewedAt());
        }

        Map<String, OcrAuthorityItemJpaEntity> managedAuthority = managed.getAuthorityItems().stream()
                .collect(Collectors.toMap(OcrAuthorityItemJpaEntity::getAuthorityId, Function.identity()));
        for (AuthorityItem a : r.authorityItems()) {
            OcrAuthorityItemJpaEntity ma = managedAuthority.get(a.authorityId().value().toString());
            if (ma == null) continue;
            ma.setDecision(a.decision().name());
            ma.setDecidedAt(a.decidedAt());
        }
    }

    // ---- audit ---------------------------------------------------------------------------------

    public OcrReviewFieldAuditJpaEntity toAuditEntity(String reviewId, FieldAuditEntry a) {
        return new OcrReviewFieldAuditJpaEntity(
                a.auditId().toString(),
                reviewId,
                a.fieldId().value().toString(),
                a.decision().name(),
                a.previousValue(),
                a.newValue(),
                a.reason(),
                a.reviewerId().toString(),
                a.reviewerRole(),
                a.occurredAt(),
                a.sequence());
    }
}
