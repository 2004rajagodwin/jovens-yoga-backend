package com.jovens.yoga.mapper;

import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.entity.CustomerOrder;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getOrderNumber(),
                order.getPlanNameSnapshot(),
                order.getDurationLabelSnapshot(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                order.getUser().getEmail()
        );
    }
}
