package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.LegalFactPort;

/** Shared translation of the classifier's time-window parameter. */
final class TimeWindows {

    private TimeWindows() {}

    static LegalFactPort.TimeWindow parse(String raw) {
        if (raw == null) return LegalFactPort.TimeWindow.ALL;
        try {
            return LegalFactPort.TimeWindow.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return LegalFactPort.TimeWindow.ALL;
        }
    }

    static String label(LegalFactPort.TimeWindow window) {
        return switch (window) {
            case TODAY      -> "hari ini";
            case THIS_MONTH -> "bulan ini";
            case THIS_YEAR  -> "tahun ini";
            case NEXT_WEEK  -> "minggu depan";
            case ALL        -> "secara keseluruhan";
        };
    }
}
