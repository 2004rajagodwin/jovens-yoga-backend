package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminUserDetailResponse;
import com.jovens.yoga.dto.response.AdminUserResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.mapper.AdminOrderMapper;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.repository.UserRepository;
import com.jovens.yoga.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final TrialRepository trialRepository;
    private final OrderRepository orderRepository;
    private final AdminOrderMapper adminOrderMapper;

    public AdminUserServiceImpl(UserRepository userRepository,
                                 TrialRepository trialRepository,
                                 OrderRepository orderRepository,
                                 AdminOrderMapper adminOrderMapper) {
        this.userRepository = userRepository;
        this.trialRepository = trialRepository;
        this.orderRepository = orderRepository;
        this.adminOrderMapper = adminOrderMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String search, Pageable pageable) {
        Page<User> users = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrMobileNumberContainingIgnoreCase(
                        search, search, search, search, pageable);

        return PageResponse.from(users.map(this::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        Optional<Trial> trial = trialRepository.findByUserId(id);

        return new AdminUserDetailResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCountryRegion(),
                user.getCountryPhoneCode(),
                user.getMobileNumber(),
                user.getAddress(),
                user.getCreatedAt(),
                trial.map(t -> t.getStatus().name()).orElse(null),
                trial.map(Trial::getTrialStartDate).orElse(null),
                trial.map(Trial::getTrialExpiryDate).orElse(null),
                orderRepository.findByUserIdOrderByCreatedAtDesc(id).stream().map(adminOrderMapper::toResponse).toList()
        );
    }

    private AdminUserResponse toSummary(User user) {
        String trialStatus = trialRepository.findByUserId(user.getId()).map(t -> t.getStatus().name()).orElse(null);
        String currentPlan = orderRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), OrderStatus.PAID)
                .map(order -> order.getPlanNameSnapshot())
                .orElse(null);

        return new AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCountryRegion(),
                user.getCountryPhoneCode(),
                user.getMobileNumber(),
                user.getCreatedAt(),
                trialStatus,
                currentPlan
        );
    }
}
