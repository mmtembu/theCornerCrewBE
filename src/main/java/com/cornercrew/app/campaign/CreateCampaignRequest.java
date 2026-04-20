package com.cornercrew.app.campaign;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCampaignRequest(
        @NotBlank String title,
        String description,
        @NotNull @Positive BigDecimal targetAmount,
        @NotNull LocalDate windowStart,
        @NotNull @Future LocalDate windowEnd
) {}
