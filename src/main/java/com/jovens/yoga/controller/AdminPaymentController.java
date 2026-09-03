package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminPaymentResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.service.AdminPaymentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(AdminPaymentService adminPaymentService) {
        this.adminPaymentService = adminPaymentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminPaymentResponse>> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Payments retrieved.", adminPaymentService.listPayments(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminPaymentResponse> getPayment(@PathVariable Long id) {
        return ApiResponse.success("Payment retrieved.", adminPaymentService.getPayment(id));
    }
}
