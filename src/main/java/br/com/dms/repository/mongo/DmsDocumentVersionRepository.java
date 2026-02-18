package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.DmsDocumentVersion;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DmsDocumentVersionRepository extends MongoRepository<DmsDocumentVersion, String> {

    Optional<DmsDocumentVersion> findByTenantIdAndDmsDocumentIdAndVersionNumber(String tenantId, String dmsDocumentId, String versionNumber);

    @Aggregation(pipeline = {
            "{ '$match': { 'tenantId': ?0, 'dmsDocumentId' : ?1 } }",
            "{ '$sort' : { 'versionNumber' : -1 } }",
            "{ '$limit' : 1 }"
    }, collation = "{ locale: 'pt', numericOrdering: true}")
    Optional<DmsDocumentVersion> findLastVersionByTenantIdAndDmsDocumentId(String tenantId, String dmsDocumentId);

    Optional<List<DmsDocumentVersion>> findByTenantIdAndDmsDocumentId(String tenantId, String dmsDocumentId);

    void deleteByTenantIdAndDmsDocumentId(String tenantId, String dmsDocumentId);

    long countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(
            String tenantId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Aggregation(pipeline = {
            "{ '$match': { 'tenantId': ?0, 'uploadStatus': 'COMPLETED' } }",
            "{ '$group': { '_id': null, 'total': { '$sum': { '$ifNull': ['$fileSize', 0] } } } }",
            "{ '$project': { '_id': 0, 'total': 1 } }"
    })
    Long sumCompletedFileSizeByTenantId(String tenantId);

    // legacy methods kept temporarily during tenant migration
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
