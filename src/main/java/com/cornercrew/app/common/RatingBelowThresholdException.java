package com.cornercrew.app.common;

public class RatingBelowThresholdException extends RuntimeException {

    private final double avgRating;
    private final double threshold;

    public RatingBelowThresholdException(double avgRating, double threshold) {
        super("Average rating " + avgRating + " is below threshold " + threshold);
        this.avgRating = avgRating;
        this.threshold = threshold;
    }

    public double getAvgRating() {
        return avgRating;
    }

    public double getThreshold() {
        return threshold;
    }
}
