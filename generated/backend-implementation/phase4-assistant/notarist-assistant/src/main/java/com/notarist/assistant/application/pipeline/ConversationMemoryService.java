package com.notarist.assistant.application.pipeline;

import com.notarist.assistant.domain.model.ConversationTurn;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory conversation history per session.
 *
 * Thread-safe: ConcurrentHashMap keyed by sessionId; each session list is synchronized.
 * Phase 4 stub — no persistence. History is lost on restart.
 * Persistent conversation storage (Oracle or PostgreSQL) deferred to Phase 5.
 *
 * Maximum 50 turns per session to bound memory growth.
 */
@Service
public class ConversationMemoryService {

    private static final int MAX_TURNS_PER_SESSION = 50;

    private final ConcurrentHashMap<UUID, List<ConversationTurn>> sessionMemory = new ConcurrentHashMap<>();

    public void store(ConversationTurn turn) {
        sessionMemory.compute(turn.sessionId(), (id, turns) -> {
            if (turns == null) turns = Collections.synchronizedList(new ArrayList<>());
            turns.add(turn);
            if (turns.size() > MAX_TURNS_PER_SESSION) {
                turns.remove(0);
            }
            return turns;
        });
    }

    public List<ConversationTurn> getHistory(UUID sessionId) {
        List<ConversationTurn> turns = sessionMemory.get(sessionId);
        if (turns == null) return List.of();
        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    /** Returns the most recent {@code limit} turns for the session. */
    public List<ConversationTurn> getRecentHistory(UUID sessionId, int limit) {
        List<ConversationTurn> all = getHistory(sessionId);
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }

    public int turnCount(UUID sessionId) {
        return getHistory(sessionId).size();
    }

    public void clearSession(UUID sessionId) {
        sessionMemory.remove(sessionId);
    }
}
