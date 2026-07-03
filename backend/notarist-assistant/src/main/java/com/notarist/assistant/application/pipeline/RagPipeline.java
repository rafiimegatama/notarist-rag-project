package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.application.command.SubmitQueryCommand;
import com.notarist.assistant.domain.model.AssistantToken;
import com.notarist.assistant.domain.model.Citation;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Contract for RAG pipeline implementations.
 * Citation-first: citations are assembled BEFORE the LLM prompt is built.
 * If citationCount == 0, pipeline throws NoSourceFoundException.
 */
public interface RagPipeline {

    /**
     * Step 1: retrieve chunks
     * Step 2: assemble citations (pre-LLM)
     * Step 3: build prompt with [CITATION-N] markers
     * Step 4: stream LLM tokens
     * Step 5: post-response hallucination check
     */
    Flux<AssistantToken> execute(SubmitQueryCommand command);

    List<Citation> assemblePreCitations(SubmitQueryCommand command);
}
