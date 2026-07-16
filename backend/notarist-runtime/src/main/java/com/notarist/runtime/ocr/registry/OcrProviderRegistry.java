package com.notarist.runtime.ocr.registry;

import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.spi.OcrProvider;
import com.notarist.runtime.ocr.spi.OcrProviderHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The <b>OCR Registry</b> — the OCR branch of the runtime registry family.
 *
 * <pre>
 *   RuntimeRegistry
 *     ├── OCR Registry        ← this class            (OCR_PROVIDER)
 *     ├── LLM Registry        ┐
 *     ├── Embedding Registry  ├─ ProviderRegistry     (LLM_PROVIDER / EMBED_PROVIDER / RERANK_PROVIDER)
 *     └── Reranker Registry   ┘
 * </pre>
 *
 * <p>It follows {@code ProviderRegistry}'s conventions deliberately — same selection key shape
 * ({@code notarist.runtime.<capability>.provider}), same lowercase-id indexing, same duplicate-id
 * rejection, same "fail with the list of ids that ARE registered" error — so the four capabilities
 * behave identically and OCR is not a special case with its own vocabulary.
 *
 * <p>It is a SEPARATE bean rather than four more fields on {@code ProviderRegistry} because OCR
 * carries state the AI capabilities do not: a startup probe, per-provider health, and the capability
 * matrix the health endpoint renders. Folding that into the shared registry would push OCR-specific
 * concerns into the LLM's file. When the unified {@code RuntimeRegistry} facade wants an
 * {@code ocr()} accessor, it is a one-line delegation to this class.
 *
 * <p>Providers are discovered by Spring — every {@link OcrProvider} bean is injected here. Adding an
 * engine is: write the class, annotate {@code @Component}, set {@code OCR_PROVIDER} to its id.
 * Nothing in this file, the port, the worker, or any shared AI config changes.
 *
 * <p><b>Selection fails at STARTUP, not at first use.</b> A typo in {@code OCR_PROVIDER=padle} must
 * not produce a service that boots green, accepts uploads, and then dead-letters every document at
 * the OCR stage. The constructor throws, naming the bad id and listing what IS available.
 */
@Component
public class OcrProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(OcrProviderRegistry.class);

    private final Map<String, OcrProvider> providers;
    private final OcrProvider active;
    private final OcrProperties properties;

    public OcrProviderRegistry(
            List<OcrProvider> discovered,
            OcrProperties properties,
            // Same key shape as notarist.runtime.{llm,embedding,reranker}.provider — one convention
            // across the whole runtime, so nobody has to remember which capability spells it differently.
            @Value("${notarist.runtime.ocr.provider:paddle}") String activeOcrId) {
        this.properties = properties;
        this.providers = index(discovered);

        String requested = normalize(activeOcrId);
        if (requested.isBlank()) {
            throw new IllegalStateException(
                    "No OCR provider selected. Set notarist.runtime.ocr.provider (env: OCR_PROVIDER). "
                    + "Registered ids: " + providers.keySet());
        }

        OcrProvider selected = providers.get(requested);
        if (selected == null) {
            throw new IllegalStateException(
                    "No OCR provider registered for id='" + requested + "'. Registered ids: "
                    + providers.keySet() + ". Fix the OCR_PROVIDER env var or add the provider bean.");
        }
        this.active = selected;

        log.info("OCR Registry initialised. active='{}' ({}) available={} capabilities=[{}]",
                active.id(), active.displayName(), providers.keySet(),
                active.capabilities().toMatrixString());
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, OcrProvider> index(List<OcrProvider> discovered) {
        Map<String, OcrProvider> byId = new LinkedHashMap<>();
        for (OcrProvider provider : discovered) {
            String id = provider.id();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        provider.getClass().getName() + " returned a blank OcrProvider.id()");
            }
            OcrProvider previous = byId.put(id.toLowerCase(), provider);
            if (previous != null) {
                // Two engines answering to the same id means OCR_PROVIDER is ambiguous and which one
                // you get depends on bean ordering. That is not something to discover in production.
                throw new IllegalStateException(
                        "Duplicate OCR provider id '" + id + "': "
                        + previous.getClass().getName() + " and " + provider.getClass().getName());
            }
        }
        if (byId.isEmpty()) {
            throw new IllegalStateException(
                    "No OcrProvider beans found. At least one OCR engine must be on the classpath.");
        }
        return Map.copyOf(byId);
    }

    /** The engine business logic will actually hit. */
    public OcrProvider active() {
        return active;
    }

    public Optional<OcrProvider> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(providers.get(id.toLowerCase()));
    }

    public Collection<OcrProvider> all() {
        return providers.values();
    }

    public java.util.Set<String> availableIds() {
        return providers.keySet();
    }

    /** Probe every engine. Used by the health endpoint. Never throws. */
    public Map<String, OcrProviderHealth> healthOfAll() {
        return providers.values().stream().collect(Collectors.toMap(
                OcrProvider::id,
                OcrProviderRegistry::safeHealth,
                (a, b) -> a,
                LinkedHashMap::new));
    }

    public OcrProviderHealth activeHealth() {
        return safeHealth(active);
    }

    /**
     * A provider's health() is contractually not allowed to throw — but a provider is third-party
     * code from this class's point of view, and a health endpoint that 500s because an engine threw
     * is worse than useless during the outage it exists to describe.
     */
    private static OcrProviderHealth safeHealth(OcrProvider provider) {
        try {
            OcrProviderHealth health = provider.health();
            return health != null
                    ? health
                    : OcrProviderHealth.unknown(provider.id(), "provider returned null health");
        } catch (Exception e) {
            log.warn("OCR provider '{}' threw from health(): {}", provider.id(), e.getMessage());
            return OcrProviderHealth.down(provider.id(), "health probe threw: " + e.getMessage());
        }
    }

    /**
     * The capability matrix: what every registered engine can do, rendered for the health endpoint.
     * Answers "could we switch to Gemini for handwriting?" by reading one endpoint rather than six
     * adapters.
     */
    public Map<String, String> capabilityMatrix() {
        return providers.values().stream().collect(Collectors.toMap(
                OcrProvider::id,
                p -> p.capabilities().toMatrixString(),
                (a, b) -> a,
                LinkedHashMap::new));
    }

    /**
     * Probe on startup so a misconfigured endpoint shows up in the boot log, next to the config that
     * caused it — rather than as a failed ingestion an hour later.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        if (!properties.isProbeOnStartup()) {
            return;
        }

        OcrProviderHealth health = activeHealth();
        if (health.isUp()) {
            log.info("OCR startup probe: provider='{}' is UP ({})", active.id(), health.detail());
            return;
        }

        String message = "OCR startup probe: provider='" + active.id() + "' is "
                + health.status() + " — " + health.detail();

        if (properties.isFailFastOnUnhealthyProvider()) {
            throw new IllegalStateException(
                    message + ". Refusing to start (notarist.ocr.fail-fast-on-unhealthy-provider=true).");
        }

        // Default: warn, do not crash. The engine may simply be slower to start than we are, and a
        // crash-looping app cannot serve the auth and document endpoints that do not need OCR at all.
        log.warn("{}. Ingestion will fail at the OCR stage until it recovers.", message);
    }
}
