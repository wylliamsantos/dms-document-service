package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByTenantIdAndName(String tenantId, String name);
    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);
    List<Category> findAllByTenantId(String tenantId);
    Optional<Category> findByIdAndTenantId(String id, String tenantId);

    // legacy methods kept temporarily during tenant migration
    Optional<Category> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
