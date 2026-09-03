package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsernameIgnoreCaseAndActiveTrue(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
