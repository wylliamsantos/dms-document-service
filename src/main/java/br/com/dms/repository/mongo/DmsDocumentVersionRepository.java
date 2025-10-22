package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.DmsDocumentVersion;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DmsDocumentVersionRepository extends MongoRepository<DmsDocumentVersion, String> {

    Optional<DmsDocumentVersion> findByDmsDocumentIdAndVersionNumber(String dmsDocumentId, String versionNumber);

    @Aggregation(pipeline = {
            "{ '$match': { 'dmsDocumentId' : ?0 } }",
            "{ '$sort' : { 'versionNumber' : -1 } }",
            "{ '$limit' : 1 }"
    }, collation = "{ locale: 'pt', numericOrdering: true}")
    Optional<DmsDocumentVersion> findLastVersionByDmsDocumentId(String dmsDocumentId);

    Optional<List<DmsDocumentVersion>> findByDmsDocumentId(String dmsDocumentId);

    void deleteByDmsDocumentId(String dmsDocumentId);

}
