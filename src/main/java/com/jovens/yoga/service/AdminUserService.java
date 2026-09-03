package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.AdminUserDetailResponse;
import com.jovens.yoga.dto.response.AdminUserResponse;
import com.jovens.yoga.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    PageResponse<AdminUserResponse> listUsers(String search, Pageable pageable);
    AdminUserDetailResponse getUser(Long id);
}
