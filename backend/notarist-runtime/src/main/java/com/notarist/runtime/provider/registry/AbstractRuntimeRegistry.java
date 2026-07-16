package com.notarist.runtime.provider.registry;

import com.notarist.runtime.provider.RuntimeProvider;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared machinery for every per-kind runtime registry (LLM / embedding / reranker), mirroring the
 * OCR module's {@code OcrProviderRegistry}: index providers by id, resolve the configured active one
 * <b>at startup</b> (not at first use), and probe health without ever throwing.
 *
 * <p>Fail-fast is the whole point: a typo in {@code LLM_PROVIDER=ollma} must not boot a green
 * service that dead-letters every request later — the constructor throws, naming the bad id and
 * listing what IS registered.
 *
 * @param <P> the provider kind
 */
public abstract class AbstractRuntimeRegistry<P extends RuntimeProvider> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String kind;
    private final Map<String, P> providers;
    private final P active;

    protected AbstractRuntimeRegistry(String kind, List<P> discovered, String requestedId) {
        this.kind = kind;
        this.providers = index(kind, discovered);
        this.active = resolve(kind, providers, requestedId);
        log.info("{} registry: active='{}' model='{}' available={} capabilities={}",
                kind, active.id(), active.activeModel(), providers.keySet(), active.capabilities());
    }

    /** The provider business logic will actually hit — always an interface, never a concrete type. */
    public P active() {
        return active;
    }

    public Optional<P> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(providers.get(normalize(id)));
    }

    public Collection<P> all() {
        return providers.values();
    }

    public Set<String> availableIds() {
        return providers.keySet();
    }

    /** Probe every provider of this kind. Never throws. */
    public Map<String, RuntimeProviderHealth> healthOfAll() {
        return providers.values().stream().collect(Collectors.toMap(
                RuntimeProvider::id, AbstractRuntimeRegistry::safeHealth, (a, b) -> a, LinkedHashMap::new));
    }

    public RuntimeProviderHealth activeHealth() {
        return safeHealth(active);
    }

    private static <P extends RuntimeProvider> Map<String, P> index(String kind, List<P> discovered) {
        Map<String, P> byId = new LinkedHashMap<>();
        for (P provider : discovered) {
            String id = provider.id();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(provider.getClass().getName() + " returned a blank " + kind + " provider id()");
            }
            P previous = byId.put(normalize(id), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate " + kind + " provider id '" + id + "': "
                        + previous.getClass().getName() + " and " + provider.getClass().getName());
            }
        }
        if (byId.isEmpty()) {
            throw new IllegalStateException("No " + kind + " provider beans found on the classpath.");
        }
        return Map.copyOf(byId);
    }

    private static <P extends RuntimeProvider> P resolve(String kind, Map<String, P> providers, String requestedId) {
        String id = normalize(requestedId);
        if (id.isEmpty()) {
            throw new IllegalStateException("No " + kind + " provider selected. Set the *_PROVIDER env var. Available: " + providers.keySet());
        }
        P selected = providers.get(id);
        if (selected == null) {
            throw new IllegalStateException(kind + " provider '" + requestedId + "' is not registered. "
                    + "Available: " + providers.keySet() + ". Fix the *_PROVIDER env var or add the provider bean.");
        }
        return selected;
    }

    private static RuntimeProviderHealth safeHealth(RuntimeProvider provider) {
        try {
            RuntimeProviderHealth h = provider.health();
            return h != null ? h : RuntimeProviderHealth.unknown(provider.id(), provider.activeModel(), "provider returned null health");
        } catch (Exception e) {
            return RuntimeProviderHealth.down(provider.id(), provider.activeModel(), "health probe threw: " + e.getMessage());
        }
    }

    protected static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
