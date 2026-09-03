package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.AdminLoginRequest;
import com.jovens.yoga.dto.response.AdminLoginResponse;

public interface AdminAuthService {
    AdminLoginResponse login(AdminLoginRequest request);
}
