package br.com.dms.repository.mongo;

import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DmsDocumentRepository extends MongoRepository<DmsDocument, String> {

    Optional<DmsDocument> findByIdAndTenantId(String id, String tenantId);

    Optional<DmsDocument> findByTenantIdAndBusinessKeyValueAndFilename(String tenantId, String businessKeyValue, String filename);

    Optional<DmsDocument> findByTenantIdAndBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(String tenantId, String businessKeyType, String businessKeyValue, String filename, String category);

    List<DmsDocument> findByTenantIdAndWorkflowStatus(String tenantId, DocumentWorkflowStatus workflowStatus);

    List<DmsDocument> findByTenantIdAndWorkflowStatusAndCategory(String tenantId, DocumentWorkflowStatus workflowStatus, String category);

    // legacy methods kept temporarily during tenant migration
    Optional<DmsDocument> findByCpfAndFilename(String cpf, String filename);

    Optional<DmsDocument> findByBusinessKeyValueAndFilename(String businessKeyValue, String filename);

    Optional<DmsDocument> findByBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(String businessKeyType, String businessKeyValue, String filename, String category);

    Optional<DmsDocument> findByCpfAndFilenameAndCategory(String cpf, String filename, String category);

    boolean existsByCpfAndFilename(String cpf, String filename);

    List<DmsDocument> findByWorkflowStatus(DocumentWorkflowStatus workflowStatus);

    List<DmsDocument> findByWorkflowStatusAndCategory(DocumentWorkflowStatus workflowStatus, String category);
}
