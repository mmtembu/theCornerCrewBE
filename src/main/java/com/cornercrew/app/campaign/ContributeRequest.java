package com.cornercrew.app.campaign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ContributeRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull ContributionPeriod period
) {}
