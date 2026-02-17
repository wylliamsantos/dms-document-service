package br.com.dms.service;

import br.com.dms.repository.mongo.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentTypeService {

    private final CategoryRepository categoryRepository;
    private final TenantContextService tenantContextService;

    public DocumentTypeService(CategoryRepository categoryRepository,
                               TenantContextService tenantContextService) {
        this.categoryRepository = categoryRepository;
        this.tenantContextService = tenantContextService;
    }

    public List<String> findByDocumentCategoryName(String documentCategoryName) {
        String tenantId = tenantContextService.requireTenantId();
        return categoryRepository.findByTenantIdAndName(tenantId, documentCategoryName)
                .map(category -> {
                    if (category.getTypes() == null) {
                        return List.<String>of();
                    }
                    List<String> names = new ArrayList<>();
                    category.getTypes().forEach(type -> names.add(type.getName()));
                    return names;
                })
                .orElse(List.of());
    }
}
