package com.jovens.yoga.mapper;

import com.jovens.yoga.dto.response.AdminOrderResponse;
import com.jovens.yoga.entity.CustomerOrder;
import org.springframework.stereotype.Component;

@Component
public class AdminOrderMapper {

    public AdminOrderResponse toResponse(CustomerOrder order) {
        return new AdminOrderResponse(
                order.getOrderNumber(),
                order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                order.getUser().getEmail(),
                order.getPlanNameSnapshot(),
                order.getDurationLabelSnapshot(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getStripeCheckoutSessionId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
