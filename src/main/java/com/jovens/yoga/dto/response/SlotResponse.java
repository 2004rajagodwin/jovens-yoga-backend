package com.jovens.yoga.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(
        Long id,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        String label,
        boolean active,
        int displayOrder
) {
}
