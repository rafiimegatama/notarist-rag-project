package com.notarist.runtime.ocr.runtime;

import com.notarist.runtime.capability.GpuAwarenessConfig;
import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.spi.OcrCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides how many documents/pages to push at an OCR engine at once.
 *
 * <p><b>No hardcoded GPU assumptions.</b> Nothing here knows what an RTX 5060 Ti is, and nothing
 * should: the batch size is derived from the DETECTED hardware profile that
 * {@link GpuAwarenessConfig} already computes from actual CUDA availability and VRAM. The same build
 * runs on a 16GB GPU, on a laptop, and on a CPU-only Cloud Run instance, and picks a different batch
 * size in each case without a code change or a per-host config file.
 *
 * <p>The resolved size is the MINIMUM of three ceilings, because any one of them can be the binding
 * constraint:
 * <ol>
 *   <li>what the operator configured ({@code notarist.ocr.batch.max-size}), if they configured it;</li>
 *   <li>what the hardware can plausibly hold, if they did not;</li>
 *   <li>what the ENGINE says it can accept ({@link OcrCapabilities#maxBatchSize()}).</li>
 * </ol>
 * Skipping (3) is the subtle bug: a batch size tuned for a 16GB local GPU, sent to a cloud OCR API
 * with a documented limit of 5 images per request, produces 400s that look like a payload problem.
 */
@Component
public class OcrBatchSizer {

    private static final Logger log = LoggerFactory.getLogger(OcrBatchSizer.class);

    /**
     * Batch size per detected hardware profile. Conservative on purpose: an OCR batch that exceeds
     * VRAM does not degrade gracefully, it OOMs the engine mid-document and takes the whole batch
     * with it. Raise these by measuring, not by guessing.
     */
    private static final int GPU_LARGE_BATCH = 8; // VRAM >= 8GB (a 16GB card lands here)
    private static final int CPU_Q8_BATCH    = 2;
    private static final int CPU_Q4_BATCH    = 1; // Memory-starved: batching would only add pressure.

    private final OcrProperties properties;
    private final GpuAwarenessConfig.HardwareProfile hardwareProfile;
    private final int hardwareDerivedSize;

    public OcrBatchSizer(OcrProperties properties,
                         GpuAwarenessConfig.HardwareProfile hardwareProfile) {
        this.properties = properties;
        this.hardwareProfile = hardwareProfile;
        this.hardwareDerivedSize = deriveFromHardware(hardwareProfile);

        log.info("OcrBatchSizer: hardwareProfile={} → hardware-derived batch size {} "
                        + "(configured override: {})",
                hardwareProfile, hardwareDerivedSize,
                properties.getBatch().getMaxSize() > 0
                        ? properties.getBatch().getMaxSize()
                        : "none — using hardware-derived");
    }

    private static int deriveFromHardware(GpuAwarenessConfig.HardwareProfile profile) {
        return switch (profile) {
            case GPU_LARGE -> GPU_LARGE_BATCH;
            case CPU_Q8 -> CPU_Q8_BATCH;
            case CPU_Q4 -> CPU_Q4_BATCH;
        };
    }

    /**
     * Effective batch size for an engine. Always >= 1, so a caller can use it unconditionally.
     */
    public int resolveFor(OcrCapabilities capabilities) {
        if (!properties.getBatch().isEnabled() || !capabilities.supportsBatch()) {
            return 1;
        }

        int configured = properties.getBatch().getMaxSize();

        // An explicit OCR_BATCH_SIZE always wins — the operator has measured something we have not.
        // Otherwise derive it, but only from hardware the ENGINE can actually use: sizing a cloud OCR
        // API's batch by the local GPU's VRAM is meaningless, because that engine never touches the
        // local device. For a non-GPU engine, the local hardware profile says nothing useful, so fall
        // back to its own advertised ceiling.
        int requested;
        if (configured > 0) {
            requested = configured;
        } else if (capabilities.supportsGpu()) {
            requested = hardwareDerivedSize;
        } else {
            requested = capabilities.maxBatchSize();
        }

        // The engine's own ceiling always wins over anything we computed.
        return Math.max(1, Math.min(requested, capabilities.maxBatchSize()));
    }

    /**
     * The hint carried on a single-document request, telling the engine how many PAGES it may push
     * through the device at once. Batching pages within one document is where a GPU actually pays
     * off for OCR — most akta are one document, many pages.
     */
    public int pageBatchHint() {
        int configured = properties.getBatch().getMaxSize();
        return configured > 0 ? configured : hardwareDerivedSize;
    }

    public GpuAwarenessConfig.HardwareProfile hardwareProfile() {
        return hardwareProfile;
    }
}
