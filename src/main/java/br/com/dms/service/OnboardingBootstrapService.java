package br.com.dms.service;

import br.com.dms.controller.request.CategoryRequest;
import br.com.dms.controller.request.OnboardingBootstrapRequest;
import br.com.dms.controller.response.OnboardingBootstrapResponse;
import br.com.dms.domain.core.DocumentGroup;
import br.com.dms.repository.mongo.CategoryRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OnboardingBootstrapService {

    private final TenantContextService tenantContextService;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    public OnboardingBootstrapService(TenantContextService tenantContextService,
                                      CategoryRepository categoryRepository,
                                      CategoryService categoryService) {
        this.tenantContextService = tenantContextService;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    public OnboardingBootstrapResponse bootstrap(OnboardingBootstrapRequest request) {
        String tenantId = tenantContextService.requireTenantId();
        String ownerUsername = resolveOwnerUsername();

        int before = categoryRepository.findAllByTenantId(tenantId).size();
        boolean created = false;
        String createdCategoryName = null;

        if (request.isCreateDefaultCategory() && before == 0) {
            String categoryName = StringUtils.trimToEmpty(request.getInitialCategoryName());
            CategoryRequest defaultCategory = CategoryRequest.builder()
                    .name(categoryName)
                    .title("Onboarding inicial")
                    .description("Categoria inicial criada automaticamente pelo bootstrap de onboarding")
                    .documentGroup(DocumentGroup.PERSONAL)
                    .uniqueAttributes("cpf")
                    .businessKeyField("cpf")
                    .active(true)
                    .schema(defaultSchema())
                    .types(List.of())
                    .build();

            createdCategoryName = categoryService.save(defaultCategory).getName();
            created = true;
        }

        int after = categoryRepository.findAllByTenantId(tenantId).size();
        return OnboardingBootstrapResponse.builder()
                .tenantId(tenantId)
                .ownerUsername(ownerUsername)
                .categoriesBefore(before)
                .categoriesAfter(after)
                .createdDefaultCategory(created)
                .createdCategoryName(createdCategoryName)
                .build();
    }

    private String resolveOwnerUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (StringUtils.isNotBlank(preferredUsername)) {
                return preferredUsername;
            }
            String email = jwt.getClaimAsString("email");
            if (StringUtils.isNotBlank(email)) {
                return email;
            }
        }

        return authentication.getName();
    }

    private Map<Object, Object> defaultSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "cpf", Map.of(
                                "type", "string",
                                "pattern", "^\\d{11}$"
                        )
                ),
                "required", List.of("cpf")
        );
    }
}
