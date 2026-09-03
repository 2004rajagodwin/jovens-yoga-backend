package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.AdminOrderResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface AdminOrderService {
    PageResponse<AdminOrderResponse> listOrders(OrderStatus status, String search, Pageable pageable);
    AdminOrderResponse getOrder(String orderNumber);
}
