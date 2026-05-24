package com.notarist.runtime.capability;

import com.notarist.runtime.model.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active model profile based on detected hardware capabilities.
 *
 * Profiles:
 *   GPU_LARGE    — VRAM ≥ 8GB; full-precision or Q8 model; inference pool=4
 *   CPU_Q8       — VRAM < 8GB or no GPU; Q8 quantised model; inference pool=1 (sequential)
 *   CPU_Q4       — freeMemory < 4GB; Q4 quantised model; reduced quality acceptable
 *
 * Model selection does NOT override ModelRegistry — it logs the recommendation
 * and activates appropriate pool size via InferenceQueueIsolation tuning.
 * Actual model name comes from application.yml / ModelRegistry defaults.
 *
 * Design note: CPU-first always; GPU is an additive capability, never required.
 */
@Configuration
public class GpuAwarenessConfig {

    private static final Logger log = LoggerFactory.getLogger(GpuAwarenessConfig.class);

    public enum HardwareProfile {
        GPU_LARGE,
        CPU_Q8,
        CPU_Q4
    }

    private final RuntimeCapabilityDetector capabilityDetector;

    public GpuAwarenessConfig(RuntimeCapabilityDetector capabilityDetector) {
        this.capabilityDetector = capabilityDetector;
    }

    @Bean
    public HardwareProfile activeHardwareProfile() {
        RuntimeCapabilityDetector.RuntimeCapability cap = capabilityDetector.detect();
        HardwareProfile profile = selectProfile(cap);
        log.info("GpuAwarenessConfig: selected profile={} based on cuda={} vramMb={} freeMemMb={}",
                profile, cap.cudaAvailable(), cap.vramMb(), cap.freeMemoryMb());
        return profile;
    }

    @Bean
    public RuntimeCapabilityDetector.RuntimeCapability runtimeCapability() {
        return capabilityDetector.detect();
    }

    private HardwareProfile selectProfile(RuntimeCapabilityDetector.RuntimeCapability cap) {
        if (cap.cudaAvailable() && cap.hasEnoughVramForLargeModel()) {
            return HardwareProfile.GPU_LARGE;
        }
        if (cap.freeMemoryMb() < 4_096) {
            return HardwareProfile.CPU_Q4;
        }
        return HardwareProfile.CPU_Q8;
    }
}
