package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminOrderResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.service.AdminOrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderResponse>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Orders retrieved.", adminOrderService.listOrders(status, search, pageable));
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<AdminOrderResponse> getOrder(@PathVariable String orderNumber) {
        return ApiResponse.success("Order retrieved.", adminOrderService.getOrder(orderNumber));
    }
}
