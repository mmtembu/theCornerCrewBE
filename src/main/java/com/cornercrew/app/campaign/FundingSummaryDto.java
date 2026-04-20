package com.cornercrew.app.campaign;

import java.math.BigDecimal;

public record FundingSummaryDto(
        BigDecimal currentTotal,
        BigDecimal remainingCapacity
) {}
