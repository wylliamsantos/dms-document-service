package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
