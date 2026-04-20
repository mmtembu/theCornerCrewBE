package com.cornercrew.app.assignment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AssignControllerRequest(
        @NotNull Long controllerId,
        @NotNull Long intersectionId,
        @NotNull @Size(min = 1) List<LocalDate> shiftDates,
        @NotNull @Positive BigDecimal agreedPay
) {}
