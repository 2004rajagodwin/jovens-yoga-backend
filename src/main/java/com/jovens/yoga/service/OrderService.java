package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.CreateOrderRequest;
import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.entity.CustomerOrder;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderStatus(String orderNumber);

    /** Returns the managed entity for internal use by PaymentService. */
    CustomerOrder getOrderEntity(String orderNumber);

    /** Marks a still-PENDING order CANCELLED. No-op (idempotent) if already in a terminal state. */
    void cancelIfPending(String orderNumber);
}
