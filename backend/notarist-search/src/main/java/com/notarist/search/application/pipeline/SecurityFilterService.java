package com.notarist.search.application.pipeline;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.search.domain.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Applies security filtering BEFORE retrieval merge.
 * Tenant isolation + classification level enforcement in-memory.
 * DB-level filtering (VPD + WHERE clause) is a first line of defence;
 * this service is a deterministic second line that must never be skipped.
 */
@Service
public class SecurityFilterService {

    private static final Logger log = LoggerFactory.getLogger(SecurityFilterService.class);

    private static final List<ClassificationLevel> LEVEL_ORDER = List.of(
            ClassificationLevel.PUBLIC,
            ClassificationLevel.INTERNAL,
            ClassificationLevel.CONFIDENTIAL,
            ClassificationLevel.STRICTLY_CONFIDENTIAL
    );

    public List<RetrievedChunk> filter(
            List<RetrievedChunk> candidates,
            UUID callerTenantId,
            ClassificationLevel callerMaxLevel) {

        List<RetrievedChunk> passed = candidates.stream()
                .filter(c -> c.tenantId().equals(callerTenantId))
                .filter(c -> isWithinBound(c.classificationLevel(), callerMaxLevel))
                .collect(Collectors.toList());

        int rejected = candidates.size() - passed.size();
        if (rejected > 0) {
            log.warn("SecurityFilter rejected {} of {} chunks — tenantId={} maxLevel={}",
                    rejected, candidates.size(), callerTenantId, callerMaxLevel);
        }
        return passed;
    }

    private boolean isWithinBound(ClassificationLevel chunkLevel, ClassificationLevel callerMax) {
        int chunkIdx  = LEVEL_ORDER.indexOf(chunkLevel);
        int callerIdx = LEVEL_ORDER.indexOf(callerMax);
        return chunkIdx >= 0 && chunkIdx <= callerIdx;
    }
}
