package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/** One row of {@code bundle_document} — a DocumentRef held by a bundle. */
@Embeddable
public class BundleDocumentEmbeddable {

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "role_in_bundle", length = 100)
    private String roleInBundle;

    protected BundleDocumentEmbeddable() {}

    public BundleDocumentEmbeddable(String documentId, String roleInBundle) {
        this.documentId = documentId;
        this.roleInBundle = roleInBundle;
    }

    public String getDocumentId() { return documentId; }
    public String getRoleInBundle() { return roleInBundle; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleDocumentEmbeddable that)) return false;
        return Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId);
    }
}
