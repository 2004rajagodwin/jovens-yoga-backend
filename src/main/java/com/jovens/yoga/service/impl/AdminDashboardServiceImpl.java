package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminDashboardResponse;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.repository.UserRepository;
import com.jovens.yoga.service.AdminDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All figures come from repository count() queries — never by loading full record sets
 * into memory.
 *
 * Note on "pending/failed payments": a {@code Payment} row is only ever created for a
 * verified Stripe webhook event (PAID/FAILED/REFUNDED); orders that are still awaiting
 * checkout completion never get a Payment row. "Pending payments" therefore reports
 * still-PENDING orders (the accurate proxy for "payment not yet resolved"), while
 * "failed payments" reports actual FAILED Payment records.
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final TrialRepository trialRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public AdminDashboardServiceImpl(UserRepository userRepository,
                                      TrialRepository trialRepository,
                                      OrderRepository orderRepository,
                                      PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.trialRepository = trialRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                trialRepository.countByStatus(TrialStatus.TRIAL_ACTIVE),
                trialRepository.countByStatus(TrialStatus.TRIAL_EXPIRED),
                trialRepository.count(),
                orderRepository.countByStatus(OrderStatus.PAID),
                paymentRepository.countByStatus(PaymentStatus.PAID),
                orderRepository.countByStatus(OrderStatus.PENDING),
                paymentRepository.countByStatus(PaymentStatus.FAILED)
        );
    }
}
