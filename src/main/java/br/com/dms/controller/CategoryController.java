package br.com.dms.controller;

import br.com.dms.controller.request.CategoryRequest;
import br.com.dms.controller.response.CategoryResponse;
import br.com.dms.service.CategoryService;
import br.com.dms.service.DocumentTypeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
@Slf4j
@PreAuthorize("hasAuthority('ROLE_DOCUMENT_VIEWER')")
public class CategoryController {

    private final CategoryService service;
    private final DocumentTypeService documentTypeService;

    public CategoryController(CategoryService service, DocumentTypeService documentTypeService) {
        this.service = service;
        this.documentTypeService = documentTypeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CategoryResponse> create(@RequestHeader(name = "TransactionId") String transactionId,
                                                   @RequestHeader(name = "Authorization") String authorization,
                                                   @RequestBody @Valid CategoryRequest categoryRequest) {
        log.info("DMS version v1 - CreateCategory - início da criação de categoria - {} - TransactionId: {}", categoryRequest.getName(), transactionId);
        return new ResponseEntity<>(service.save(categoryRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CategoryResponse> update(@RequestHeader(name = "TransactionId") String transactionId,
                                                   @RequestHeader(name = "Authorization") String authorization,
                                                   @PathVariable("id") String id, @RequestBody @Valid CategoryRequest categoryRequest) {
        log.info("DMS version v1 - UpdateCategory - início da alteração de categoria - {} - TransactionId: {}", categoryRequest.getName(), transactionId);
        return new ResponseEntity<>(service.update(id, categoryRequest), HttpStatus.OK);
    }

    @GetMapping("/all")
    public ResponseEntity<List<CategoryResponse>> findAll(@RequestHeader(name = "TransactionId", required = true) String transactionId,
                                                          @RequestHeader(name = "Authorization", required = true) String authorization) {
        log.info("DMS version v1 - ListCategories - início da listagem de categorias - TransactionId: {}", transactionId);
        return new ResponseEntity<>(service.findAll(), HttpStatus.OK);
    }

    @GetMapping("/{categoryName}/types")
    public ResponseEntity<?> findTypesByCategory(@RequestHeader(name = "TransactionId") String transactionId,
                                                  @RequestHeader(name = "Authorization") String authorization,
                                                  @PathVariable("categoryName") String categoryName) {
        log.info("DMS version v1 - List document types for category {} - TransactionId: {}", categoryName, transactionId);
        return new ResponseEntity<>(documentTypeService.findByDocumentCategoryName(categoryName), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@RequestHeader(name = "TransactionId") String transactionId,
                                                     @RequestHeader(name = "Authorization") String authorization,
                                                     @PathVariable("id") String id) {
        log.info("DMS version v1 - FindCategory - início da busca da categoria - {} - TransactionId: {}", id, transactionId);
        return new ResponseEntity<>(service.findById(id), HttpStatus.OK);
    }
}
