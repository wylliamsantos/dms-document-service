package br.com.dms.service;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DocumentRetentionJobService {

    private final CategoryRepository categoryRepository;
    private final DmsDocumentRepository dmsDocumentRepository;

    @Value("${dms.retention.enabled:true}")
    private boolean enabled;

    @Value("${dms.retention.batch-size:500}")
    private int batchSize;

    public DocumentRetentionJobService(CategoryRepository categoryRepository, DmsDocumentRepository dmsDocumentRepository) {
        this.categoryRepository = categoryRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
    }

    @Scheduled(cron = "${dms.retention.cron:0 */30 * * * *}")
    public void execute() {
        if (!enabled) {
            return;
        }

        List<Category> categories = categoryRepository.findAll();
        int archived = 0;
        int deleted = 0;

        for (Category category : categories) {
            if ((category.getArchiveAfterDays() == null || category.getArchiveAfterDays() <= 0)
                && (category.getRetentionDays() == null || category.getRetentionDays() <= 0)) {
                continue;
            }

            List<DmsDocument> documents = dmsDocumentRepository.findByTenantIdAndCategory(category.getTenantId(), category.getName());
            if (documents.isEmpty()) {
                continue;
            }

            List<DmsDocument> toArchive = new ArrayList<>();
            List<DmsDocument> toDelete = new ArrayList<>();

            for (DmsDocument document : documents) {
                Instant createdAt = extractCreatedAt(document.getId());
                if (createdAt == null) {
                    continue;
                }

                long ageInDays = ChronoUnit.DAYS.between(createdAt, Instant.now());

                if (category.getRetentionDays() != null && ageInDays >= category.getRetentionDays()) {
                    document.setExpiredAt(Instant.now());
                    toDelete.add(document);
                    continue;
                }

                if (category.getArchiveAfterDays() != null
                    && ageInDays >= category.getArchiveAfterDays()
                    && !Boolean.TRUE.equals(document.getArchived())) {
                    document.setArchived(Boolean.TRUE);
                    document.setArchivedAt(Instant.now());
                    toArchive.add(document);
                }
            }

            if (!toArchive.isEmpty()) {
                archived += saveInBatches(toArchive);
            }

            if (!toDelete.isEmpty()) {
                deleted += deleteInBatches(toDelete);
            }
        }

        if (archived > 0 || deleted > 0) {
            log.info("DMS - retention cycle finished: archived={}, deleted={}", archived, deleted);
        }
    }

    private int saveInBatches(List<DmsDocument> documents) {
        int total = 0;
        for (int i = 0; i < documents.size(); i += Math.max(batchSize, 1)) {
            int end = Math.min(i + Math.max(batchSize, 1), documents.size());
            dmsDocumentRepository.saveAll(documents.subList(i, end));
            total += (end - i);
        }
        return total;
    }

    private int deleteInBatches(List<DmsDocument> documents) {
        int total = 0;
        for (int i = 0; i < documents.size(); i += Math.max(batchSize, 1)) {
            int end = Math.min(i + Math.max(batchSize, 1), documents.size());
            dmsDocumentRepository.deleteAll(documents.subList(i, end));
            total += (end - i);
        }
        return total;
    }

    private Instant extractCreatedAt(String id) {
        try {
            if (id == null || !ObjectId.isValid(id)) {
                return null;
            }
            return new ObjectId(id).getDate().toInstant();
        } catch (Exception ex) {
            log.debug("DMS - could not resolve createdAt from id {}", id, ex);
            return null;
        }
    }
}
