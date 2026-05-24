package com.notarist.runtime.capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Detects hardware capabilities at startup for GPU-aware model selection.
 *
 * Detection strategy:
 *   1. CUDA: checks CUDA_VISIBLE_DEVICES env var; if set and non-empty → GPU present.
 *      Optionally invokes nvidia-smi for VRAM amount.
 *   2. CPU cores: Runtime.getRuntime().availableProcessors()
 *   3. Free heap: Runtime.getRuntime().freeMemory() + maxMemory - totalMemory
 *
 * All detection is best-effort and non-fatal; defaults to CPU-only on failure.
 */
@Component
public class RuntimeCapabilityDetector {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCapabilityDetector.class);

    public record RuntimeCapability(
            boolean cudaAvailable,
            long    vramMb,
            int     cpuCores,
            long    freeMemoryMb
    ) {
        public boolean hasEnoughVramForLargeModel() { return vramMb >= 8_192; }
        public boolean hasEnoughVramForMediumModel(){ return vramMb >= 4_096; }
    }

    public RuntimeCapability detect() {
        boolean cudaAvailable = detectCuda();
        long    vramMb        = cudaAvailable ? detectVramMb() : 0L;
        int     cpuCores      = Runtime.getRuntime().availableProcessors();
        long    freeMemMb     = detectFreeMemoryMb();

        RuntimeCapability capability = new RuntimeCapability(cudaAvailable, vramMb, cpuCores, freeMemMb);

        log.info("RuntimeCapabilityDetector: cuda={} vramMb={} cpuCores={} freeMemMb={}",
                cudaAvailable, vramMb, cpuCores, freeMemMb);

        return capability;
    }

    private boolean detectCuda() {
        String cudaDevices = System.getenv("CUDA_VISIBLE_DEVICES");
        if (cudaDevices != null && !cudaDevices.isBlank() && !cudaDevices.equals("-1")) {
            log.debug("RuntimeCapabilityDetector: CUDA_VISIBLE_DEVICES={}", cudaDevices);
            return true;
        }
        // Fallback: try nvidia-smi
        return nvidiaSmIPresent();
    }

    private boolean nvidiaSmIPresent() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"nvidia-smi", "--query-gpu=name", "--format=csv,noheader"});
            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("RuntimeCapabilityDetector: nvidia-smi not found — assuming CPU-only");
            return false;
        }
    }

    private long detectVramMb() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{
                    "nvidia-smi", "--query-gpu=memory.total", "--format=csv,noheader,nounits"
            });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            log.debug("RuntimeCapabilityDetector: could not read VRAM from nvidia-smi: {}", e.getMessage());
        }
        return 0L;
    }

    private long detectFreeMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        long freeBytes = rt.maxMemory() - rt.totalMemory() + rt.freeMemory();
        return freeBytes / (1024 * 1024);
    }
}
