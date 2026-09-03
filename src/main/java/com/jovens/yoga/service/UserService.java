package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.CustomerDetailsRequest;
import com.jovens.yoga.entity.User;

public interface UserService {

    /**
     * Finds the existing customer by email or mobile number, or creates a new one.
     * An existing customer's details are refreshed with the latest submission.
     * Throws BusinessException if the email and mobile number already belong to two
     * different existing customer records (identity conflict).
     */
    User findOrCreateCustomer(CustomerDetailsRequest request);
}
