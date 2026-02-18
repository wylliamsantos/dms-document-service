package br.com.dms.service;

import br.com.dms.controller.request.CategoryRequest;
import br.com.dms.controller.request.OnboardingBootstrapRequest;
import br.com.dms.controller.response.CategoryResponse;
import br.com.dms.controller.response.OnboardingBootstrapResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.repository.mongo.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingBootstrapServiceTest {

    private final TenantContextService tenantContextService = mock(TenantContextService.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final CategoryService categoryService = mock(CategoryService.class);

    private final OnboardingBootstrapService service = new OnboardingBootstrapService(
            tenantContextService,
            categoryRepository,
            categoryService
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateDefaultCategoryWhenTenantHasNoCategories() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(categoryRepository.findAllByTenantId("tenant-dev")).thenReturn(List.of(), List.of(new Category()));
        when(categoryService.save(any(CategoryRequest.class))).thenReturn(CategoryResponse.builder().name("onboarding-basico").build());

        var request = OnboardingBootstrapRequest.builder()
                .createDefaultCategory(true)
                .initialCategoryName("onboarding-basico")
                .build();

        OnboardingBootstrapResponse response = service.bootstrap(request);

        ArgumentCaptor<CategoryRequest> captor = ArgumentCaptor.forClass(CategoryRequest.class);
        verify(categoryService, times(1)).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("onboarding-basico");
        assertThat(captor.getValue().getBusinessKeyField()).isEqualTo("cpf");

        assertThat(response.isCreatedDefaultCategory()).isTrue();
        assertThat(response.getCategoriesBefore()).isZero();
        assertThat(response.getCategoriesAfter()).isEqualTo(1);
        assertThat(response.getCreatedCategoryName()).isEqualTo("onboarding-basico");
        assertThat(response.getTenantId()).isEqualTo("tenant-dev");
    }

    @Test
    void shouldSkipCreationWhenTenantAlreadyHasCategories() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(categoryRepository.findAllByTenantId("tenant-dev")).thenReturn(List.of(new Category()), List.of(new Category()));

        var request = OnboardingBootstrapRequest.builder()
                .createDefaultCategory(true)
                .initialCategoryName("onboarding-basico")
                .build();

        OnboardingBootstrapResponse response = service.bootstrap(request);

        verify(categoryService, never()).save(any());
        assertThat(response.isCreatedDefaultCategory()).isFalse();
        assertThat(response.getCategoriesBefore()).isEqualTo(1);
        assertThat(response.getCategoriesAfter()).isEqualTo(1);
        assertThat(response.getCreatedCategoryName()).isNull();
    }
}
