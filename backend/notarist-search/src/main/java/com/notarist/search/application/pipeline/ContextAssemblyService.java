package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.AssembledContext;
import com.notarist.search.domain.model.CitationEntry;
import com.notarist.search.domain.model.GroundingScore;
import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Final assembly step — builds AssembledContext from ranked chunks.
 * Order: citations resolved → grounding computed → budget applied.
 * Citations are always resolved BEFORE budget truncation to ensure full provenance.
 */
@Service
public class ContextAssemblyService {

    private final CitationResolver citationResolver;
    private final GroundingValidator groundingValidator;
    private final ContextBudgetManager budgetManager;

    public ContextAssemblyService(
            CitationResolver citationResolver,
            GroundingValidator groundingValidator,
            ContextBudgetManager budgetManager) {
        this.citationResolver  = citationResolver;
        this.groundingValidator = groundingValidator;
        this.budgetManager     = budgetManager;
    }

    public AssembledContext assemble(
            String normalizedQuery,
            List<RetrievedChunk> rankedChunks,
            int tokenBudget) {

        // Citations resolved from full ranked list — before truncation
        List<CitationEntry> citations = citationResolver.resolve(rankedChunks);

        // Grounding validated against full ranked list
        GroundingScore groundingScore = groundingValidator.validate(normalizedQuery, rankedChunks);

        // Token budget applied last
        ContextBudgetManager.BudgetResult budget = budgetManager.applyBudget(rankedChunks, tokenBudget);

        return new AssembledContext(
                budget.selectedChunks(),
                citations,
                groundingScore,
                budget.estimatedTokenCount(),
                budget.truncated(),
                budget.totalCandidates());
    }
}
