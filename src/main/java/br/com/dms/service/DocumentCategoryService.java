package br.com.dms.service;

import br.com.dms.repository.mongo.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class DocumentCategoryService {

    private final CategoryRepository categoryRepository;

    public DocumentCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<String> findAllDocumentCategoryNames() {
        return StreamSupport.stream(categoryRepository.findAll().spliterator(), false)
                .map(category -> category.getName())
                .collect(Collectors.toList());
    }
}
