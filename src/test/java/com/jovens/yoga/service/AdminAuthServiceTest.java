package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.AdminLoginRequest;
import com.jovens.yoga.dto.response.AdminLoginResponse;
import com.jovens.yoga.entity.Admin;
import com.jovens.yoga.enums.AdminRole;
import com.jovens.yoga.repository.AdminRepository;
import com.jovens.yoga.security.JwtService;
import com.jovens.yoga.service.impl.AdminAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AdminAuthServiceImpl adminAuthService;

    @Test
    void loginSucceedsWithCorrectCredentials() {
        Admin admin = new Admin();
        admin.setUsername("Jovensyoga2026");
        admin.setName("Admin");
        admin.setPasswordHash("hashed");
        admin.setRole(AdminRole.ADMIN);

        when(adminRepository.findByUsernameIgnoreCaseAndActiveTrue("Jovensyoga2026"))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.generateToken("Jovensyoga2026", "ADMIN")).thenReturn("jwt-token");

        AdminLoginResponse response = adminAuthService.login(new AdminLoginRequest("Jovensyoga2026", "correct-password"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("Jovensyoga2026");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    @Test
    void loginFailsWithWrongPassword() {
        Admin admin = new Admin();
        admin.setUsername("Jovensyoga2026");
        admin.setPasswordHash("hashed");
        admin.setRole(AdminRole.ADMIN);

        when(adminRepository.findByUsernameIgnoreCaseAndActiveTrue("Jovensyoga2026"))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("Jovensyoga2026", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginFailsWhenAdminNotFound() {
        when(adminRepository.findByUsernameIgnoreCaseAndActiveTrue("missing-user"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("missing-user", "any")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginFailsForInactiveAdmin() {
        // Inactive admins simply aren't returned by the active-only lookup, so this
        // exercises the same not-found path as loginFailsWhenAdminNotFound.
        when(adminRepository.findByUsernameIgnoreCaseAndActiveTrue("Jovensyoga2026"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("Jovensyoga2026", "any")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
