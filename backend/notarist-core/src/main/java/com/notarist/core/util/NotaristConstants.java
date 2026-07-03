package com.notarist.core.util;

/** System-wide constants shared across ingest pipeline and embedding infrastructure. */
public final class NotaristConstants {

    private NotaristConstants() {}

    public static final int EMBEDDING_DIMENSION = 1024;

    public static final int AKTA_CHUNK_MAX_TOKENS      = 512;
    public static final int AKTA_CHUNK_MIN_TOKENS      = 128;
    public static final int AKTA_CHUNK_OVERLAP_PCT     = 15;

    public static final int REGULASI_CHUNK_MAX_TOKENS  = 768;
    public static final int REGULASI_CHUNK_MIN_TOKENS  = 256;

    public static final int SOP_CHUNK_MAX_TOKENS       = 400;
    public static final int SOP_CHUNK_MIN_TOKENS       = 100;
    public static final int SOP_CHUNK_OVERLAP_PCT      = 10;
}
