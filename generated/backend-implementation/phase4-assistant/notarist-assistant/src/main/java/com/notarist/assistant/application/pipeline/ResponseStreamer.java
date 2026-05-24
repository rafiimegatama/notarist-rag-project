package com.notarist.assistant.application.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarist.assistant.api.response.AssistantResponse;
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
 * Emission sequence:
 *   1. ANSWER_TOKEN — one per sentence (buffered; never raw token)
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

    public ResponseStreamer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void stream(AssistantResponse response, SseEmitter emitter) {
        AtomicInteger seq = new AtomicInteger(0);
        UUID traceId = response.trace().traceId();

        try {
            // 1. Stream answer sentence by sentence
            String[] sentences = SENTENCE_SPLIT.split(response.answerText());
            for (String sentence : sentences) {
                if (!sentence.isBlank()) {
                    emit(emitter, SseEvent.answerToken(sentence.trim(), traceId, seq.getAndIncrement()));
                }
            }

            // 2. Stream citations
            for (CitationDto citation : response.citations()) {
                emit(emitter, SseEvent.citation(toJson(citation), traceId, seq.getAndIncrement()));
            }

            // 3. Stream confidence
            String confidenceData = response.confidence().name()
                    + " | score=" + String.format("%.2f", response.groundingScore());
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
            emitter.complete();

        } catch (Exception e) {
            log.error("SSE streaming error traceId={}: {}", traceId, e.getMessage(), e);
            emitter.completeWithError(e);
        }
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
