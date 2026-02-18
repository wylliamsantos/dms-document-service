package br.com.dms.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnboardingBootstrapResponse {

    private String tenantId;
    private String ownerUsername;
    private int categoriesBefore;
    private int categoriesAfter;
    private boolean createdDefaultCategory;
    private String createdCategoryName;
}
