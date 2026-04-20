package com.cornercrew.app.common;

import java.math.BigDecimal;

public class ContributionExceedsCapException extends RuntimeException {

    private final BigDecimal remainingCapacity;

    public ContributionExceedsCapException(BigDecimal remainingCapacity) {
        super("Contribution exceeds cap; remaining capacity is " + remainingCapacity);
        this.remainingCapacity = remainingCapacity;
    }

    public BigDecimal getRemainingCapacity() {
        return remainingCapacity;
    }
}
