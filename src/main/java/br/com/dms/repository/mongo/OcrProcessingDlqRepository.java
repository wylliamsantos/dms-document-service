package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.OcrProcessingDlq;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OcrProcessingDlqRepository extends MongoRepository<OcrProcessingDlq, String> {
}
