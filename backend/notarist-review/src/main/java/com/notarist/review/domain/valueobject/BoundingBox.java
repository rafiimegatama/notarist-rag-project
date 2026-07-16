package com.notarist.review.domain.valueobject;

/**
 * Where a field was found on the page. Coordinates are RELATIVE ([0..1]) to the page, so the frontend
 * can overlay the highlight against any rendered page size without knowing the source resolution.
 *
 * @param page   1-based page number the field appears on
 * @param x      left edge, 0..1
 * @param y      top edge, 0..1
 * @param width  width, 0..1
 * @param height height, 0..1
 */
public record BoundingBox(int page, double x, double y, double width, double height) {

    public BoundingBox {
        if (page < 1) throw new IllegalArgumentException("bounding box page must be >= 1");
        requireUnit("x", x);
        requireUnit("y", y);
        requireUnit("width", width);
        requireUnit("height", height);
    }

    public static BoundingBox of(int page, double x, double y, double width, double height) {
        return new BoundingBox(page, x, y, width, height);
    }

    private static void requireUnit(String name, double v) {
        if (v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException("bounding box " + name + " must be within [0,1] (was " + v + ")");
        }
    }
}
