package com.cornercrew.app.assignment;

import java.math.BigDecimal;

public record PayoutResultDto(
        Long assignmentId,
        BigDecimal agreedPay,
        double avgRating
) {}
