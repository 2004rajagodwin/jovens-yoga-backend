package com.jovens.yoga.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jovens.yoga.dto.request.AdminLoginRequest;
import com.jovens.yoga.entity.Admin;
import com.jovens.yoga.enums.AdminRole;
import com.jovens.yoga.repository.AdminRepository;
import com.jovens.yoga.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Admin seedAdmin(String username, String rawPassword, boolean active) {
        Admin admin = new Admin();
        admin.setName("Test Admin");
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(AdminRole.ADMIN);
        admin.setActive(active);
        return adminRepository.save(admin);
    }

    @Test
    void loginSucceedsWithCorrectUsernameAndPassword() throws Exception {
        seedAdmin("login-success-user", "CorrectPass123!", true);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("login-success-user", "CorrectPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.username").value("login-success-user"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void passwordIsNeverReturnedInLoginResponse() throws Exception {
        seedAdmin("no-password-leak-user", "CorrectPass123!", true);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("no-password-leak-user", "CorrectPass123!"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CorrectPass123!"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("passwordHash"))));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        seedAdmin("wrong-password-user", "CorrectPass123!", true);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("wrong-password-user", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void loginFailsWithUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("no-such-admin-user", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void loginFailsForInactiveAdmin() throws Exception {
        seedAdmin("inactive-admin-user", "CorrectPass123!", false);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("inactive-admin-user", "CorrectPass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void customerCannotAccessAdminApisWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTokenIsRejectedOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validAdminTokenGrantsAccessToDashboard() throws Exception {
        Admin admin = seedAdmin("dashboard-access-user", "CorrectPass123!", true);
        String token = jwtService.generateToken(admin.getUsername(), admin.getRole().name());

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").exists());
    }
}
