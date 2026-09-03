package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AdminSlotRequest(
        @NotNull LocalDate slotDate,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        String label,
        boolean active,
        int displayOrder
) {
}
