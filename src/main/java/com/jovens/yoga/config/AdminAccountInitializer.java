package com.jovens.yoga.config;

import com.jovens.yoga.entity.Admin;
import com.jovens.yoga.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial admin account from environment variables on startup, if an admin
 * with that username doesn't already exist. Idempotent by username — safe to run on
 * every startup. Keeps the credential out of Flyway migrations (which are committed to
 * source control) and lets the password be hashed with the live encoder.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedUsername;
    private final String seedName;
    private final String seedPassword;

    public AdminAccountInitializer(AdminRepository adminRepository,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${app.admin.seed-username:Jovensyoga2026}") String seedUsername,
                                    @Value("${app.admin.seed-name:Jovens Yoga Admin}") String seedName,
                                    @Value("${app.admin.seed-password:}") String seedPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsername = seedUsername;
        this.seedName = seedName;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.existsByUsernameIgnoreCase(seedUsername)) {
            return;
        }

        if (seedPassword == null || seedPassword.isBlank()) {
            log.warn("No admin account named '{}' exists and ADMIN_SEED_PASSWORD is not set. " +
                    "Set ADMIN_SEED_USERNAME / ADMIN_SEED_PASSWORD and restart to create it.", seedUsername);
            return;
        }

        Admin admin = new Admin();
        admin.setName(seedName);
        admin.setUsername(seedUsername);
        admin.setPasswordHash(passwordEncoder.encode(seedPassword));
        admin.setActive(true);
        adminRepository.save(admin);

        log.info("Seeded initial admin account: {}", seedUsername);
    }
}
