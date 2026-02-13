package br.com.dms.repository.mongo;

import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DmsDocumentRepository extends MongoRepository<DmsDocument, String> {

    Optional<DmsDocument> findByCpfAndFilename(String cpf, String filename);

    Optional<DmsDocument> findByCpfAndFilenameAndCategory(String cpf, String filename, String category);

    boolean existsByCpfAndFilename(String cpf, String filename);

    List<DmsDocument> findByWorkflowStatus(DocumentWorkflowStatus workflowStatus);

    List<DmsDocument> findByWorkflowStatusAndCategory(DocumentWorkflowStatus workflowStatus, String category);
}
