package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class DocumentInsightResponse {
    String documentId;
    String version;
    String summary;
    Map<String, Object> keyMetadata;
    List<String> warnings;
    double confidence;
    String confidenceBand;
    String source;
    String generatedAt;
    List<InsightSignalResponse> signals;
    Map<String, Object> persistedMetadataPreview;
    Map<String, Object> importantPersistedMetadata;
    Integer persistedMetadataCount;
    Boolean hasPersistedOcrText;
    List<String> expectedRequiredMetadata;
    List<String> missingRequiredMetadata;
    Integer requiredMetadataCoveragePercent;
    List<MetadataActionHintResponse> metadataActionHints;
    List<MetadataUpdateHistoryEntryResponse> metadataUpdateHistory;
    List<MetadataRegressionAlertResponse> metadataRegressionAlerts;
    Map<String, Object> ocrStats;
}
