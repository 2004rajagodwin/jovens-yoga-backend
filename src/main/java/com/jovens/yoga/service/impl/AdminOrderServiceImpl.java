package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminOrderResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.mapper.AdminOrderMapper;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.service.AdminOrderService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final AdminOrderMapper adminOrderMapper;

    public AdminOrderServiceImpl(OrderRepository orderRepository, AdminOrderMapper adminOrderMapper) {
        this.orderRepository = orderRepository;
        this.adminOrderMapper = adminOrderMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> listOrders(OrderStatus status, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search;
        return PageResponse.from(orderRepository.search(status, normalizedSearch, pageable).map(adminOrderMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrder(String orderNumber) {
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        return adminOrderMapper.toResponse(order);
    }
}
