package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.DocumentWorkflowTransition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentWorkflowTransitionRepository extends MongoRepository<DocumentWorkflowTransition, String> {
}
