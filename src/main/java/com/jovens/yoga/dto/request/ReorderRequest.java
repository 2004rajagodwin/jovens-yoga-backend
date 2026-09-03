package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderRequest(@NotEmpty List<Long> orderedIds) {
}
