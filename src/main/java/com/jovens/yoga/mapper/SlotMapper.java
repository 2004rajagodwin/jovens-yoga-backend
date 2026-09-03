package com.jovens.yoga.mapper;

import com.jovens.yoga.dto.response.SlotResponse;
import com.jovens.yoga.entity.ClassSlot;
import org.springframework.stereotype.Component;

@Component
public class SlotMapper {

    public SlotResponse toResponse(ClassSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getCapacity(),
                slot.getLabel(),
                slot.isActive(),
                slot.getDisplayOrder()
        );
    }
}
