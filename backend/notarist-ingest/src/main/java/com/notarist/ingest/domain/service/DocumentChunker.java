package com.notarist.ingest.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic token-window chunker for extracted document text.
 * Zero Spring dependency.
 *
 * Splits text into whitespace-delimited tokens and emits sliding windows of
 * targetTokens with overlapTokens carried between consecutive windows.
 * Character offsets refer to the original text, so every chunk is exactly
 * reproducible from the source object.
 *
 * Indonesian legal documents are organised by "Pasal" (article). The chunker
 * tracks the most recent Pasal heading preceding each window and records it
 * as the chunk's section title / pasal reference, which downstream feeds the
 * Qdrant pasal_reference payload field and citation rendering.
 */
public final class DocumentChunker {

    private static final Pattern PASAL_HEADING =
            Pattern.compile("(?i)\\bpasal\\s+(\\d+[A-Za-z]?)\\b");

    private DocumentChunker() {}

    public record TextChunk(
            int index,
            String text,
            int startOffset,
            int endOffset,
            int tokenCount,
            String sectionTitle,
            String pasalRef
    ) {}

    private record Token(int startChar, int endChar) {}

    private record PasalMark(int charOffset, String reference) {}

    public static List<TextChunk> chunk(String text, int targetTokens, int overlapTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (targetTokens < 1) {
            throw new IllegalArgumentException("targetTokens must be >= 1, got: " + targetTokens);
        }
        if (overlapTokens < 0) {
            throw new IllegalArgumentException("overlapTokens must be >= 0, got: " + overlapTokens);
        }

        List<Token> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<PasalMark> pasalMarks = findPasalMarks(text);
        int step = Math.max(1, targetTokens - overlapTokens);

        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(start + targetTokens, tokens.size());
            int startChar = tokens.get(start).startChar();
            int endChar = tokens.get(end - 1).endChar();

            String pasalRef = latestPasalBefore(pasalMarks, endChar);
            chunks.add(new TextChunk(
                    index++,
                    text.substring(startChar, endChar),
                    startChar,
                    endChar,
                    end - start,
                    pasalRef != null ? "Pasal " + pasalRef : null,
                    pasalRef));

            if (end == tokens.size()) {
                break;
            }
        }
        return chunks;
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("\\S+").matcher(text);
        while (m.find()) {
            tokens.add(new Token(m.start(), m.end()));
        }
        return tokens;
    }

    private static List<PasalMark> findPasalMarks(String text) {
        List<PasalMark> marks = new ArrayList<>();
        Matcher m = PASAL_HEADING.matcher(text);
        while (m.find()) {
            marks.add(new PasalMark(m.start(), m.group(1)));
        }
        return marks;
    }

    private static String latestPasalBefore(List<PasalMark> marks, int charOffset) {
        String latest = null;
        for (PasalMark mark : marks) {
            if (mark.charOffset() >= charOffset) {
                break;
            }
            latest = mark.reference();
        }
        return latest;
    }
}
