package com.jovens.yoga.controller;

import com.jovens.yoga.entity.*;
import com.jovens.yoga.enums.*;
import com.jovens.yoga.repository.*;
import com.jovens.yoga.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Wraps each test in a rolled-back transaction so the seeded plan/user/order/payment/
// trial/notification rows never leak into other test classes sharing the same H2 instance.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminManagementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminRepository adminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private PlanRepository planRepository;
    @Autowired private PlanDurationRepository planDurationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TrialRepository trialRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private NotificationRepository notificationRepository;

    private String adminToken;
    private User seededUser;
    private CustomerOrder seededOrder;
    private Notification failedNotification;

    @BeforeEach
    void setUp() {
        Admin admin = new Admin();
        admin.setName("Admin Mgmt Test");
        admin.setUsername("admin-mgmt-test-" + System.nanoTime());
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRole(AdminRole.ADMIN);
        admin.setActive(true);
        adminRepository.save(admin);
        adminToken = jwtService.generateToken(admin.getUsername(), admin.getRole().name());

        Plan plan = new Plan();
        plan.setName("Standard");
        plan.setPlanType(PlanType.STANDARD);
        plan.setCurrency("USD");
        plan.setActive(true);
        plan = planRepository.save(plan);

        PlanDuration duration = new PlanDuration();
        duration.setPlan(plan);
        duration.setDurationLabel("Per Month");
        duration.setDurationValue(1);
        duration.setDurationUnit(DurationUnit.MONTH);
        duration.setPrice(new BigDecimal("29.00"));
        duration.setCurrency("USD");
        duration.setActive(true);
        duration = planDurationRepository.save(duration);

        seededUser = new User();
        seededUser.setFirstName("Mia");
        seededUser.setLastName("Chen");
        seededUser.setEmail("mia.chen." + System.nanoTime() + "@example.com");
        seededUser.setCountryPhoneCode("+1");
        seededUser.setMobileNumber("444" + (System.nanoTime() % 10000000));
        seededUser = userRepository.save(seededUser);

        Trial trial = new Trial();
        trial.setUser(seededUser);
        trial.setPlan(plan);
        trial.setTrialStartDate(LocalDateTime.now().minusDays(1));
        trial.setTrialExpiryDate(LocalDateTime.now().plusDays(4));
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        trialRepository.save(trial);

        seededOrder = new CustomerOrder();
        seededOrder.setOrderNumber("ORD-ADMIN-TEST-" + System.nanoTime());
        seededOrder.setUser(seededUser);
        seededOrder.setPlan(plan);
        seededOrder.setPlanDuration(duration);
        seededOrder.setPlanNameSnapshot("Standard");
        seededOrder.setDurationLabelSnapshot("Per Month");
        seededOrder.setAmount(new BigDecimal("29.00"));
        seededOrder.setCurrency("USD");
        seededOrder.setStatus(OrderStatus.PAID);
        seededOrder = orderRepository.save(seededOrder);

        Payment payment = new Payment();
        payment.setOrder(seededOrder);
        payment.setStripeEventId("evt_admin_test_" + System.nanoTime());
        payment.setAmount(new BigDecimal("29.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        failedNotification = new Notification();
        failedNotification.setUser(seededUser);
        failedNotification.setType(NotificationType.TRIAL_ACTIVATION);
        failedNotification.setChannel(NotificationChannel.EMAIL);
        failedNotification.setEventKey("TEST_RETRY_EVENT_" + System.nanoTime());
        failedNotification.setReferenceType("TRIAL");
        failedNotification.setReferenceId(trial.getId());
        failedNotification.setStatus(NotificationStatus.FAILED);
        failedNotification.setFailureReason("Simulated SMTP failure");
        failedNotification = notificationRepository.save(failedNotification);
    }

    @Test
    void dashboardReturnsStatistics() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.activeTrials").isNumber())
                .andExpect(jsonPath("$.data.paidOrders").isNumber());
    }

    @Test
    void listUsersSupportsSearchAndPagination() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .param("search", seededUser.getEmail())
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value(seededUser.getEmail()))
                .andExpect(jsonPath("$.data.content[0].trialStatus").value("TRIAL_ACTIVE"));
    }

    @Test
    void getUserDetailIncludesOrderHistory() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + seededUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(seededUser.getEmail()))
                .andExpect(jsonPath("$.data.orders[0].status").value("PAID"));
    }

    @Test
    void listOrdersFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PAID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void getOrderByOrderNumber() throws Exception {
        mockMvc.perform(get("/api/admin/orders/" + seededOrder.getOrderNumber())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(seededOrder.getOrderNumber()))
                .andExpect(jsonPath("$.data.amount").value(29.00));
    }

    @Test
    void listPaymentsFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .param("status", "PAID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"));
    }

    @Test
    void listTrialsFiltersByStatusAndSearch() throws Exception {
        mockMvc.perform(get("/api/admin/trials")
                        .param("status", "TRIAL_ACTIVE")
                        .param("search", seededUser.getEmail())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value(seededUser.getEmail()));
    }

    @Test
    void listNotificationsAndRetryFailedOne() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                        .param("status", "FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/notifications/" + failedNotification.getId() + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Notification reloaded = notificationRepository.findById(failedNotification.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(NotificationStatus.SENT, reloaded.getStatus());
    }

    @Test
    void retryingAlreadySentNotificationReturnsConflict() throws Exception {
        failedNotification.setStatus(NotificationStatus.SENT);
        notificationRepository.save(failedNotification);

        mockMvc.perform(post("/api/admin/notifications/" + failedNotification.getId() + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
