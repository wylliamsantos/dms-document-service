package br.com.dms.domain.mongodb.mapper;

import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.service.workflow.pojo.DmsEntry;
import org.apache.commons.lang3.StringUtils;

public class DmsDocumentMapper {

    private DmsDocumentMapper() {
        throw new IllegalStateException("Utility class");
    }

    public static DmsDocument of(DmsEntry entry) {
        final String versionKey = entry.getProperties().keySet().stream().filter(key -> StringUtils.containsIgnoreCase(key, "cpf")).findFirst().orElse("");
        final String cpf = entry.getProperties().get(versionKey).toString();

        return DmsDocument.of()
                .id(entry.getId())
                .cpf(cpf)
                .businessKeyType("cpf")
                .businessKeyValue(cpf)
                .category(entry.getCategory())
                .filename(entry.getName())
                .mimeType(entry.getContent().getMimeType())
                .build();
    }

}
