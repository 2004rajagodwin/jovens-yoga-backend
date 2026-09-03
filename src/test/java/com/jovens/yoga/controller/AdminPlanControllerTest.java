package com.jovens.yoga.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovens.yoga.dto.request.AdminPlanDurationRequest;
import com.jovens.yoga.dto.request.AdminPlanFeatureRequest;
import com.jovens.yoga.dto.request.AdminPlanRequest;
import com.jovens.yoga.entity.Admin;
import com.jovens.yoga.enums.AdminRole;
import com.jovens.yoga.repository.AdminRepository;
import com.jovens.yoga.repository.PlanRepository;
import com.jovens.yoga.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminPlansEndpointRejectsUnauthenticatedRequests() throws Exception {
        // No AuthenticationEntryPoint is configured, so Spring Security's default
        // access-denied handling returns 403 rather than 401 for missing credentials.
        mockMvc.perform(get("/api/admin/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAdminCanCreatePlan() throws Exception {
        Admin admin = new Admin();
        admin.setName("Test Admin");
        admin.setUsername("test-admin");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRole(AdminRole.ADMIN);
        admin.setActive(true);
        adminRepository.save(admin);

        String token = jwtService.generateToken(admin.getUsername(), admin.getRole().name());

        AdminPlanRequest request = new AdminPlanRequest(
                "Premium",
                "Complete wellness experience",
                null,
                "PREMIUM",
                "USD",
                null,
                true,
                "Best choice",
                true,
                0,
                List.of(new AdminPlanDurationRequest(null, "Per Month", 1, "MONTH", new BigDecimal("59.00"), "USD", 0, true)),
                List.of(new AdminPlanFeatureRequest(null, "Live classes", 0, true))
        );

        mockMvc.perform(post("/api/admin/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, planRepository.count());
    }
}
