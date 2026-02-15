package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.DocumentWorkflowTransition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentWorkflowTransitionRepository extends MongoRepository<DocumentWorkflowTransition, String> {

    List<DocumentWorkflowTransition> findByTenantIdAndDocumentIdOrderByChangedAtDesc(String tenantId, String documentId);

    // legacy methods kept temporarily during tenant migration
    List<DocumentWorkflowTransition> findByDocumentIdOrderByChangedAtDesc(String documentId);
}
