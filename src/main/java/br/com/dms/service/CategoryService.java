package br.com.dms.service;

import br.com.dms.controller.request.CategoryRequest;
import br.com.dms.controller.response.CategoryResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static br.com.dms.domain.Messages.*;

@Service
public class CategoryService {

    private final ModelMapper modelMapper;

    private final CategoryRepository repository;

    public CategoryService(ModelMapper modelMapper, CategoryRepository repository) {
        this.modelMapper = modelMapper;
        this.repository = repository;
    }

    public CategoryResponse save(CategoryRequest request){
        if(repository.existsByNameIgnoreCase(request.getName())){
            throw new DmsBusinessException(CATEGORY_EXISTS, TypeException.VALID);
        }

        validateBusinessKeyConfig(request);

        Category category = modelMapper.map(request, Category.class);
        if(category.getActive() == null){
            category.setActive(Boolean.TRUE);
        }
        var entity = repository.save(category);
        return modelMapper.map(entity, CategoryResponse.class);
    }

    public CategoryResponse update(String id, CategoryRequest request){
        if(Strings.isBlank(id)){
            throw new DmsBusinessException(BAD_REQUEST, TypeException.VALID);
        }
        Category categoryFromDB = repository.findById(id)
                .orElseThrow(()-> new DmsBusinessException(CATEGORY_NOT_FOUND, TypeException.VALID));

        if(!request.getName().equalsIgnoreCase(categoryFromDB.getName()) && repository.existsByNameIgnoreCase(request.getName())){
            throw new DmsBusinessException(CATEGORY_EXISTS, TypeException.VALID);
        }

        validateBusinessKeyConfig(request);

        Boolean requestedActive = request.getActive();
        modelMapper.map(request, categoryFromDB);
        if(requestedActive != null){
            categoryFromDB.setActive(requestedActive);
        } else if(categoryFromDB.getActive() == null){
            categoryFromDB.setActive(Boolean.TRUE);
        }
        categoryFromDB = repository.save(categoryFromDB);
        return modelMapper.map(categoryFromDB, CategoryResponse.class);
    }

    public List<CategoryResponse> findAll() {
        var categories = repository.findAll();
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

    }

    public CategoryResponse findById(String id) {
        var category = repository.findById(id).orElseThrow(()-> new DmsBusinessException(CATEGORY_NOT_FOUND, TypeException.VALID));
        return mapToResponse(category);
    }

    public Category getCategoryByName(String name){
        Category category = this.repository.findByName(name)
                .orElseThrow(() -> new DmsBusinessException(CATEGORY_NOT_FOUND, TypeException.VALID));

        if (category.getActive() != null && Boolean.FALSE.equals(category.getActive())) {
            throw new DmsBusinessException(CATEGORY_INACTIVE, TypeException.VALID);
        }

        if (category.getActive() == null) {
            category.setActive(Boolean.TRUE);
        }

        return category;
    }

    private CategoryResponse mapToResponse(Category category) {
        var response = modelMapper.map(category, CategoryResponse.class);
        if(response.getActive() == null){
            response.setActive(Boolean.TRUE);
        }
        return response;
    }

    private void validateBusinessKeyConfig(CategoryRequest request) {
        String businessKeyField = StringUtils.trimToEmpty(request.getBusinessKeyField());
        if (businessKeyField.isEmpty()) {
            throw new DmsBusinessException("businessKeyField é obrigatório", TypeException.VALID);
        }

        List<String> uniqueAttributes = Arrays.stream(StringUtils.defaultString(request.getUniqueAttributes()).split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .toList();

        if (uniqueAttributes.isEmpty() || uniqueAttributes.stream().noneMatch(attr -> attr.equalsIgnoreCase(businessKeyField))) {
            throw new DmsBusinessException("businessKeyField deve estar presente em uniqueAttributes", TypeException.VALID);
        }

        Map<Object, Object> schema = request.getSchema();
        Object requiredObj = schema != null ? schema.get("required") : null;
        if (!(requiredObj instanceof List<?> requiredFields) || requiredFields.stream().noneMatch(field -> businessKeyField.equalsIgnoreCase(String.valueOf(field)))) {
            throw new DmsBusinessException("businessKeyField deve estar na lista required do schema", TypeException.VALID);
        }
    }

}
