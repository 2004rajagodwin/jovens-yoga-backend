package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.AdminSlotRequest;
import com.jovens.yoga.dto.response.SlotResponse;
import com.jovens.yoga.entity.ClassSlot;

import java.util.List;

public interface SlotService {

    List<SlotResponse> getActiveSlots();

    List<SlotResponse> getAllSlotsForAdmin();

    /** Returns the managed entity for internal use by other services (e.g. trial creation). */
    ClassSlot getActiveSlotEntity(Long id);

    SlotResponse getSlotForAdmin(Long id);

    SlotResponse createSlot(AdminSlotRequest request);

    SlotResponse updateSlot(Long id, AdminSlotRequest request);

    SlotResponse setActive(Long id, boolean active);

    void deleteSlot(Long id);
}
