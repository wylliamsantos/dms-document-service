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
import java.util.Map;
import java.util.Optional;

public class DmsEntryMapper {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private DmsEntryMapper() {
        throw new IllegalStateException("Utility class");
    }

    public static DmsEntry of(String id, LocalDateTime createdAt, LocalDateTime modifiedAt, String filename, String category, String mimeType, Long fileSize, Map<String, Object> properties, String version, String versionType) {
        var entry = new DmsEntry();

        entry.setModifiedAt(Optional.ofNullable(modifiedAt).map(modified -> modified.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER)).orElse(null));
        entry.setCreatedAt(Optional.ofNullable(createdAt).map(created -> created.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER)).orElse(null));
        entry.setName(filename);
        entry.setId(id);
        entry.setCategory(category);
        entry.setProperties(properties);
        entry.setVersion(version);
        entry.setVersionType(versionType);

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

}
