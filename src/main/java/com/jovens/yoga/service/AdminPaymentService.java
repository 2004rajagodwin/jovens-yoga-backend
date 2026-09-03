package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.AdminPaymentResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

public interface AdminPaymentService {
    PageResponse<AdminPaymentResponse> listPayments(PaymentStatus status, Pageable pageable);
    AdminPaymentResponse getPayment(Long id);
}
