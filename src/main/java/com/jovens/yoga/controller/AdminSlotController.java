package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.AdminSlotRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.SlotResponse;
import com.jovens.yoga.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/slots")
public class AdminSlotController {

    private final SlotService slotService;

    public AdminSlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public ApiResponse<List<SlotResponse>> getAllSlots() {
        return ApiResponse.success("Class slots retrieved.", slotService.getAllSlotsForAdmin());
    }

    @GetMapping("/{id}")
    public ApiResponse<SlotResponse> getSlot(@PathVariable Long id) {
        return ApiResponse.success("Class slot retrieved.", slotService.getSlotForAdmin(id));
    }

    @PostMapping
    public ApiResponse<SlotResponse> createSlot(@Valid @RequestBody AdminSlotRequest request) {
        return ApiResponse.success("Class slot created.", slotService.createSlot(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SlotResponse> updateSlot(@PathVariable Long id, @Valid @RequestBody AdminSlotRequest request) {
        return ApiResponse.success("Class slot updated.", slotService.updateSlot(id, request));
    }

    @PatchMapping("/{id}/active")
    public ApiResponse<SlotResponse> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ApiResponse.success("Class slot status updated.", slotService.setActive(id, Boolean.TRUE.equals(body.get("active"))));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSlot(@PathVariable Long id) {
        slotService.deleteSlot(id);
        return ApiResponse.success("Class slot deleted.", null);
    }
}
