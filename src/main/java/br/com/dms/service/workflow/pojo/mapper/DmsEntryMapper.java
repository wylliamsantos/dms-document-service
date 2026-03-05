package br.com.dms.service.workflow.pojo.mapper;

import br.com.dms.service.workflow.pojo.DmsEntry;
import br.com.dms.service.workflow.pojo.DmsContent;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MimeType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class DmsEntryMapper {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private DmsEntryMapper() {
        throw new IllegalStateException("Utility class");
    }

    public static DmsEntry of(String id, LocalDateTime createdAt, LocalDateTime modifiedAt, String filename, String category, String mimeType, Long fileSize, Map<String, Object> properties, String version, String versionType, String workflowStatus, String ocrText) {
        var entry = new DmsEntry();

        entry.setModifiedAt(Optional.ofNullable(modifiedAt).map(modified -> modified.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER)).orElse(null));
        entry.setCreatedAt(Optional.ofNullable(createdAt).map(created -> created.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER)).orElse(null));
        entry.setName(filename);
        entry.setId(id);
        entry.setCategory(category);
        entry.setProperties(properties);
        entry.setVersion(version);
        entry.setVersionType(versionType);
        entry.setWorkflowStatus(workflowStatus);
        entry.setOcrSummary(buildOcrSummary(ocrText));
        entry.setImportantExtractedMetadata(extractImportantMetadata(properties));

        var content = new DmsContent();
        content.setMimeType(mimeType);
        content.setSizeInBytes(fileSize.intValue());

        String mimeTypeName;
        try {
            TikaConfig tikaConfig = new TikaConfig();
            MimeType mime = tikaConfig.getMimeRepository().forName(mimeType);
            mimeTypeName = mime.getName();
        } catch (TikaException | IOException e) {
            throw new DmsBusinessException(e.getMessage(), TypeException.VALID);
        }

        content.setMimeTypeName(mimeTypeName);
        entry.setContent(content);

        return entry;
    }

    private static String buildOcrSummary(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return null;
        }
        String normalized = ocrText.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 280) + "…";
    }

    private static Map<String, Object> extractImportantMetadata(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        var prioritized = new LinkedHashMap<String, Object>();
        properties.forEach((key, value) -> {
            String normalized = key == null ? "" : key.trim().toLowerCase();
            if (normalized.contains("cpf") || normalized.contains("cnpj") || normalized.contains("valor")
                    || normalized.contains("total") || normalized.contains("venc") || normalized.contains("emiss")
                    || normalized.contains("numero") || normalized.contains("número")) {
                prioritized.put(key, value);
            }
        });

        if (prioritized.isEmpty()) {
            return properties.entrySet().stream()
                    .limit(5)
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getKey(), item.getValue()), LinkedHashMap::putAll);
        }
        return prioritized;
    }

}
