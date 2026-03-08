package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MetadataUpdateHistoryTenantCategorySummaryResponse {
    private int totalCategories;
    private int totalDocuments;
    private int totalEntries;
    private int filteredEntries;
    private String latestUpdatedAt;
    private List<MetadataUpdateHistoryTenantCategoryBucketResponse> categories;
}
