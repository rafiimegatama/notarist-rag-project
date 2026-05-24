package com.notarist.assistant.domain.model;

/**
 * Versioned prompt template. Stored with every response for audit and reproducibility.
 * Changing the template MUST bump the version string.
 */
public record PromptVersion(
        String version,
        String description,
        String systemPromptTemplate,
        String userPromptTemplate
) {
    /**
     * v1.0.0 — Indonesian notary/PPAT legal domain.
     * {CONTEXT} is the placeholder replaced with assembled retrieval chunks.
     * {USER_QUERY} is replaced with the normalized user question.
     */
    public static final PromptVersion V1_LEGAL_ID = new PromptVersion(
            "v1.0.0-legal-id",
            "Indonesian notary and PPAT legal document assistant — citation-first, no hallucination",
            """
            Kamu adalah asisten dokumen hukum untuk kantor notaris dan PPAT Indonesia.

            ATURAN WAJIB:
            1. Hanya gunakan informasi dari dokumen yang disediakan dalam KONTEKS DOKUMEN di bawah.
            2. Jangan berasumsi, menginferensi, atau mengarang informasi di luar konteks yang diberikan.
            3. Setiap klaim penting WAJIB menyertakan referensi dalam format [Sumber: {chunk_id}].
            4. Jika konteks tidak mencukupi, jawab dengan kalimat: "Saya tidak menemukan dasar dokumen yang cukup untuk memastikan jawaban ini."
            5. Regulasi yang lebih spesifik mengalahkan regulasi yang lebih umum.
            6. Sertakan nomor akta, tanggal, dan nama pejabat yang relevan bila tersedia dalam konteks.
            7. Jawab dalam Bahasa Indonesia yang formal, jelas, dan terstruktur.
            8. Jangan menambahkan informasi hukum dari pengetahuan umum yang tidak ada dalam dokumen.

            KONTEKS DOKUMEN:
            {CONTEXT}
            """,
            """
            Pertanyaan: {USER_QUERY}

            Berikan jawaban berdasarkan dokumen yang tersedia di atas.
            Sertakan referensi [Sumber: chunk_id] untuk setiap klaim penting.
            Jika informasi tidak tersedia dalam dokumen, nyatakan dengan jelas.
            """
    );

    public String buildSystemPrompt(String assembledContext) {
        return systemPromptTemplate.replace("{CONTEXT}", assembledContext);
    }

    public String buildUserPrompt(String userQuery) {
        return userPromptTemplate.replace("{USER_QUERY}", userQuery);
    }
}
