package com.kekwy.iarnet.sdk;

import java.time.Duration;
import java.util.Objects;

public final class Windows {
    private Windows() {
    }

    public static Window join(Duration lowerBound, Duration upperBound) {
        Objects.requireNonNull(lowerBound, "lowerBound");
        Objects.requireNonNull(upperBound, "upperBound");
        return new IntervalWindow(lowerBound, upperBound);
    }

    private record IntervalWindow(Duration lowerBound, Duration upperBound) implements Window {
    }
}
