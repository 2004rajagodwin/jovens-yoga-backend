package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.CreateOrderRequest;
import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.mapper.OrderMapper;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.PlanDurationRepository;
import com.jovens.yoga.service.OrderService;
import com.jovens.yoga.service.OtpService;
import com.jovens.yoga.service.PlanService;
import com.jovens.yoga.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PlanDurationRepository planDurationRepository;
    private final PlanService planService;
    private final UserService userService;
    private final OrderMapper orderMapper;
    private final OtpService otpService;

    public OrderServiceImpl(OrderRepository orderRepository,
                             PlanDurationRepository planDurationRepository,
                             PlanService planService,
                             UserService userService,
                             OrderMapper orderMapper,
                             OtpService otpService) {
        this.orderRepository = orderRepository;
        this.planDurationRepository = planDurationRepository;
        this.planService = planService;
        this.userService = userService;
        this.orderMapper = orderMapper;
        this.otpService = otpService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        otpService.assertVerifiedAndConsume(request.otpToken(), request.customer().mobileNumber());

        Plan plan = planService.getActivePlanEntity(request.planId());

        if (plan.getPlanType() == PlanType.FREE_TRIAL) {
            throw new BusinessException("Free Trial plans cannot be purchased. Use the trial endpoint instead.",
                    "INVALID_PLAN_TYPE", HttpStatus.BAD_REQUEST);
        }

        PlanDuration duration = planDurationRepository.findById(request.durationId())
                .orElseThrow(() -> new ResourceNotFoundException("Duration not found with id: " + request.durationId()));

        if (duration.getPlan() == null || !duration.getPlan().getId().equals(plan.getId())) {
            throw new BusinessException("The selected duration does not belong to the selected plan.",
                    "DURATION_PLAN_MISMATCH", HttpStatus.BAD_REQUEST);
        }

        if (!duration.isActive()) {
            throw new BusinessException("The selected duration is not currently available.",
                    "DURATION_INACTIVE", HttpStatus.BAD_REQUEST);
        }

        User user = userService.findOrCreateCustomer(request.customer());

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setUser(user);
        order.setPlan(plan);
        order.setPlanDuration(duration);
        order.setPlanNameSnapshot(plan.getName());
        order.setDurationLabelSnapshot(duration.getDurationLabel());
        order.setAmount(duration.getPrice());
        order.setCurrency(duration.getCurrency());
        order.setStatus(OrderStatus.PENDING);

        CustomerOrder saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderStatus(String orderNumber) {
        return orderMapper.toResponse(getOrderEntity(orderNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrderEntity(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
    }

    @Override
    @Transactional
    public void cancelIfPending(String orderNumber) {
        CustomerOrder order = getOrderEntity(orderNumber);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
        }
    }
}
