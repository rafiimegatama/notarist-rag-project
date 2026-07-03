package com.notarist.document.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentLegalResponse(
    UUID documentId,
    String documentTitle,
    String documentType,
    String jenisAkta,
    String nomorAkta,
    String tanggalAkta,
    String classificationLevel,
    String status,
    Integer pageCount,
    Long fileSizeBytes,
    String mimeType,
    UUID notarisId,
    String notarisName,
    int versionNumber,
    String createdAt,
    String indexedAt
) {}
