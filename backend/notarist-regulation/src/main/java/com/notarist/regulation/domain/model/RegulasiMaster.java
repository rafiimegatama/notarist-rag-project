package com.notarist.regulation.domain.model;

import com.notarist.core.domain.valueobject.DocumentId;

import java.time.LocalDate;
import java.util.UUID;

/** Aggregate root for regulation documents — hierarchy: Regulasi → BAB → Pasal → Ayat. */
public class RegulasiMaster {

    private final UUID regulasiId;
    private final DocumentId documentId;
    private final String nomorRegulasi;
    private final String judulRegulasi;
    private final JenisRegulasi jenisRegulasi;
    private final LocalDate tanggalBerlaku;
    private final LocalDate tanggalDitetapkan;
    private RegulasiStatus status;
    private final UUID tenantId;
    private int babCount;
    private int pasalCount;

    public RegulasiMaster(
            UUID regulasiId,
            DocumentId documentId,
            String nomorRegulasi,
            String judulRegulasi,
            JenisRegulasi jenisRegulasi,
            LocalDate tanggalBerlaku,
            LocalDate tanggalDitetapkan,
            UUID tenantId) {
        this.regulasiId = regulasiId;
        this.documentId = documentId;
        this.nomorRegulasi = nomorRegulasi;
        this.judulRegulasi = judulRegulasi;
        this.jenisRegulasi = jenisRegulasi;
        this.tanggalBerlaku = tanggalBerlaku;
        this.tanggalDitetapkan = tanggalDitetapkan;
        this.status = RegulasiStatus.BERLAKU;
        this.tenantId = tenantId;
    }

    public UUID getRegulasiId() { return regulasiId; }
    public DocumentId getDocumentId() { return documentId; }
    public String getNomorRegulasi() { return nomorRegulasi; }
    public String getJudulRegulasi() { return judulRegulasi; }
    public JenisRegulasi getJenisRegulasi() { return jenisRegulasi; }
    public LocalDate getTanggalBerlaku() { return tanggalBerlaku; }
    public LocalDate getTanggalDitetapkan() { return tanggalDitetapkan; }
    public RegulasiStatus getStatus() { return status; }
    public UUID getTenantId() { return tenantId; }
    public int getBabCount() { return babCount; }
    public int getPasalCount() { return pasalCount; }

    public enum JenisRegulasi { UU, PP, PERMEN, PERDA, SK, SE }
    public enum RegulasiStatus { BERLAKU, DICABUT, DIUBAH }
}
