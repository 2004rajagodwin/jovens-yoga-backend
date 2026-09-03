package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.CustomerDetailsRequest;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.repository.UserRepository;
import com.jovens.yoga.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User findOrCreateCustomer(CustomerDetailsRequest request) {
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(request.email());
        Optional<User> byMobile = userRepository.findByMobileNumber(request.mobileNumber());

        if (byEmail.isPresent() && byMobile.isPresent() && !byEmail.get().getId().equals(byMobile.get().getId())) {
            throw new BusinessException(
                    "This email and mobile number are already associated with different accounts.",
                    "CUSTOMER_IDENTITY_CONFLICT",
                    HttpStatus.CONFLICT
            );
        }

        User user = byEmail.orElseGet(() -> byMobile.orElseGet(User::new));

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setCountryRegion(request.countryRegion());
        user.setCountryPhoneCode(request.countryPhoneCode());
        user.setMobileNumber(request.mobileNumber());
        user.setAddress(request.address());

        return userRepository.save(user);
    }
}
