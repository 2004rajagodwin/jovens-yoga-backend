package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.SlotResponse;
import com.jovens.yoga.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/active")
    public ApiResponse<List<SlotResponse>> getActiveSlots() {
        return ApiResponse.success("Active class slots retrieved.", slotService.getActiveSlots());
    }
}
