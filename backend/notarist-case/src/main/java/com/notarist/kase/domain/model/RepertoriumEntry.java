package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.RepertoriumEntryId;

import java.time.Instant;

/**
 * One line of the notary's statutory register: a sequence number, the deed it names, and when.
 *
 * <p>Immutable and never deleted. A missing number is a regulatory finding, so there is no operation
 * anywhere in this codebase that removes or renumbers one.
 */
public record RepertoriumEntry(
        RepertoriumEntryId entryId,
        CaseId caseId,
        NomorAkta nomorAkta,
        int sequence,
        Instant allocatedAt
) {
    public RepertoriumEntry {
        if (entryId == null)   throw new IllegalArgumentException("entryId is required");
        if (caseId == null)    throw new IllegalArgumentException("caseId is required");
        if (nomorAkta == null) throw new IllegalArgumentException("nomorAkta is required");
        if (sequence < 1)      throw new IllegalArgumentException("repertorium sequence starts at 1");
    }
}
