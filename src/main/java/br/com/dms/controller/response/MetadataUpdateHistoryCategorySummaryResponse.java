package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MetadataUpdateHistoryCategorySummaryResponse {
    private String category;
    private int totalDocumentsInCategory;
    private int totalDocumentsWithUpdates;
    private int totalEntries;
    private int filteredEntries;
    private String latestUpdatedAt;
    private List<MetadataUpdateHistoryBucketResponse> bySource;
    private List<MetadataUpdateHistoryBucketResponse> byField;
    private long ocrHintAppliedEntries;
    private long ocrHintCancelledEntries;
    private long ocrHintErrorEntries;
    private double ocrHintAppliedRate;
}
