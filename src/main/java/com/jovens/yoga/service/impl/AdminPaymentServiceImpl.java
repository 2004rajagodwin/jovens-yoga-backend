package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminPaymentResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.service.AdminPaymentService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {

    private final PaymentRepository paymentRepository;

    public AdminPaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> listPayments(PaymentStatus status, Pageable pageable) {
        return PageResponse.from(paymentRepository.search(status, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return toResponse(payment);
    }

    private AdminPaymentResponse toResponse(Payment payment) {
        boolean isTrial = payment.getTrial() != null;
        String orderNumber = payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null;
        String customerName = payment.getOrder() != null
                ? payment.getOrder().getUser().getFirstName() + " " + payment.getOrder().getUser().getLastName()
                : (isTrial ? payment.getTrial().getUser().getFirstName() + " " + payment.getTrial().getUser().getLastName() : null);

        return new AdminPaymentResponse(
                payment.getId(),
                orderNumber,
                customerName,
                payment.getStripeCheckoutSessionId(),
                payment.getStripePaymentIntentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                isTrial ? "TRIAL" : "ORDER",
                isTrial ? payment.getTrial().getId() : null
        );
    }
}
