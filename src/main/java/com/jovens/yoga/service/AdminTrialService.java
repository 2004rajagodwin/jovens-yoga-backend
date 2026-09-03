package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.AdminTrialResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.TrialStatus;
import org.springframework.data.domain.Pageable;

public interface AdminTrialService {
    PageResponse<AdminTrialResponse> listTrials(TrialStatus status, String search, Pageable pageable);
    AdminTrialResponse getTrial(Long id);
}
