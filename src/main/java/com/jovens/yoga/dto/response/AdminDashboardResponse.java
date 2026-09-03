package com.jovens.yoga.dto.response;

public record AdminDashboardResponse(
        long totalUsers,
        long activeTrials,
        long expiredTrials,
        long freeRegistrations,
        long paidOrders,
        long successfulPayments,
        long pendingPayments,
        long failedPayments
) {
}
