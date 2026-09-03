package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.AdminLoginRequest;
import com.jovens.yoga.dto.response.AdminLoginResponse;
import com.jovens.yoga.entity.Admin;
import com.jovens.yoga.repository.AdminRepository;
import com.jovens.yoga.security.JwtService;
import com.jovens.yoga.service.AdminAuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password.";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthServiceImpl(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        // Same generic message whether the username doesn't exist, the admin is inactive,
        // or the password is wrong — never reveal which case it was.
        Admin admin = adminRepository.findByUsernameIgnoreCaseAndActiveTrue(request.username())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        admin.setLastLoginAt(LocalDateTime.now());

        String token = jwtService.generateToken(admin.getUsername(), admin.getRole().name());
        return new AdminLoginResponse(token, admin.getUsername(), admin.getName(), admin.getRole().name());
    }
}
