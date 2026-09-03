package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.CreateOrderRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success("Order created.", orderService.createOrder(request));
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable String orderNumber) {
        return ApiResponse.success("Order retrieved.", orderService.getOrderStatus(orderNumber));
    }

    @PostMapping("/{orderNumber}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable String orderNumber) {
        orderService.cancelIfPending(orderNumber);
        return ApiResponse.success("Order cancelled.", null);
    }
}
