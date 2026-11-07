package com.ecommerce.dto.request;

import com.ecommerce.common.constant.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserCriteria {
    private String keyword;
    private RoleEnum role;
    private Boolean active;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 15;
    @Builder.Default
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDir = "desc";
}
