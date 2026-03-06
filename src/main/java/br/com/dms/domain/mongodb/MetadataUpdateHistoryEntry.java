package br.com.dms.domain.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateHistoryEntry {
    private String field;
    private String previousValue;
    private String newValue;
    private String source;
    private String updatedAt;
    private String updatedBy;
}
