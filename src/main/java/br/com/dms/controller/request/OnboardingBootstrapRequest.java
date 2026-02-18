package br.com.dms.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnboardingBootstrapRequest {

    @Builder.Default
    private boolean createDefaultCategory = true;

    @NotBlank
    private String initialCategoryName;
}
