package com.notarist.assistant.application.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarist.assistant.api.response.AssistantResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.api.response.SseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Streams a structured AssistantResponse via SSE.
 *
 * Two modes:
 *   - live streaming (AssistantController.askStream): emitToken(...) per LLM token as it is
 *     generated, then streamTail(...) for everything after the answer.
 *   - whole-response ({@link #stream}): the finished answer is chunked by sentence. Used when
 *     no token-level source is available.
 *
 * Emission sequence:
 *   1. ANSWER_TOKEN — one per LLM token (live) or one per sentence (whole-response)
 *   2. CITATION     — one per citation entry
 *   3. CONFIDENCE   — single event with grounding level and score
 *   4. WARNING      — one per warning string
 *   5. FOLLOW_UP    — one per follow-up question
 *   6. DONE         — final event with trace metadata
 *
 * Citations are emitted AFTER the full answer so the client can render
 * them in a separate panel, correctly synchronized.
 */
@Service
public class ResponseStreamer {

    private static final Logger log = LoggerFactory.getLogger(ResponseStreamer.class);
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final ObjectMapper objectMapper;

    public ResponseStreamer(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Emits ONE live LLM token as an ANSWER_TOKEN event.
     *
     * <p>Used by the SSE endpoint while the model is still generating. Throws IOException when
     * the client is gone — the caller must treat that as a cancellation signal, not as an
     * inference failure.
     */
    public void emitToken(SseEmitter emitter, String token, UUID traceId, int sequence) throws IOException {
        emit(emitter, SseEvent.answerToken(token, traceId, sequence));
    }

    /**
     * Emits everything that follows the answer text — citations, confidence, warnings,
     * follow-ups, DONE — and completes the emitter.
     *
     * @param startSequence      next free sequence number (tokens already emitted consume 0..n)
     * @param answerAlreadySent  true when the answer was live-streamed token by token; false when
     *                           the pipeline short-circuited (e.g. INSUFFICIENT grounding in
     *                           STRICT mode) and never reached the LLM, in which case the
     *                           response's answerText is emitted here instead.
     */
    public void streamTail(AssistantResponse response, SseEmitter emitter,
                           int startSequence, boolean answerAlreadySent) {
        AtomicInteger seq = new AtomicInteger(startSequence);
        UUID traceId = response.trace().traceId();

        try {
            if (!answerAlreadySent) {
                emitAnswerBySentence(response, emitter, traceId, seq);
            } else if (response.downgraded()) {
                // The raw tokens are already on the wire but the hallucination guard rejected the
                // answer. We cannot un-send them, so the client is told explicitly, and the
                // CONFIDENCE event below carries the downgraded level.
                emit(emitter, SseEvent.warning(
                        "Jawaban diturunkan oleh hallucination guard — " + response.answerText(),
                        traceId, seq.getAndIncrement()));
            }
            emitTail(response, emitter, traceId, seq);
            emitter.complete();

        } catch (Exception e) {
            log.error("SSE streaming error traceId={}: {}", traceId, e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    public void stream(AssistantResponse response, SseEmitter emitter) {
        AtomicInteger seq = new AtomicInteger(0);
        UUID traceId = response.trace().traceId();

        try {
            emitAnswerBySentence(response, emitter, traceId, seq);
            emitTail(response, emitter, traceId, seq);
            emitter.complete();

        } catch (Exception e) {
            log.error("SSE streaming error traceId={}: {}", traceId, e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    private void emitAnswerBySentence(AssistantResponse response, SseEmitter emitter,
                                      UUID traceId, AtomicInteger seq) throws IOException {
        String[] sentences = SENTENCE_SPLIT.split(response.answerText());
        for (String sentence : sentences) {
            if (!sentence.isBlank()) {
                emit(emitter, SseEvent.answerToken(sentence.trim(), traceId, seq.getAndIncrement()));
            }
        }
    }

    private void emitTail(AssistantResponse response, SseEmitter emitter,
                          UUID traceId, AtomicInteger seq) throws IOException {
        // 2. Stream citations
        for (CitationDto citation : response.citations()) {
            emit(emitter, SseEvent.citation(toJson(citation), traceId, seq.getAndIncrement()));
        }

        // 3. Stream confidence
        // Locale.ROOT: the default locale formats the score as "0,87" on a comma-decimal JVM
        // (id_ID is a likely deployment locale) and clients parse this off the SSE wire.
        String confidenceData = response.confidence().name()
                + " | score=" + String.format(java.util.Locale.ROOT, "%.2f", response.groundingScore());
        emit(emitter, SseEvent.confidence(confidenceData, traceId, seq.getAndIncrement()));

        // 4. Stream warnings
        for (String warning : response.warnings()) {
            emit(emitter, SseEvent.warning(warning, traceId, seq.getAndIncrement()));
        }

        // 5. Stream follow-up questions
        for (String followUp : response.followUpQuestions()) {
            emit(emitter, SseEvent.followUp(followUp, traceId, seq.getAndIncrement()));
        }

        // 6. Done event
        emit(emitter, SseEvent.done(traceId.toString(), traceId, seq.get()));
    }

    private void emit(SseEmitter emitter, SseEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event.eventType())
                .data(toJson(event)));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
