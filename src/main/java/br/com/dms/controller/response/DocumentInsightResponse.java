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
    String importantPersistedMetadataSummary;
    Integer importantPersistedMetadataCount;
    Integer importantExpectedMetadataCount;
    Integer importantMissingMetadataCount;
    Integer importantMetadataCoveragePercent;
    Integer persistedMetadataCount;
    Boolean hasPersistedOcrText;
    String persistedOcrExcerpt;
    List<String> expectedRequiredMetadata;
    List<String> missingRequiredMetadata;
    Integer requiredMetadataCoveragePercent;
    List<MetadataActionHintResponse> metadataActionHints;
    List<MetadataUpdateHistoryEntryResponse> metadataUpdateHistory;
    List<MetadataRegressionAlertResponse> metadataRegressionAlerts;
    MetadataUpdateOcrHintAdoptionResponse ocrHintAdoption;
    Integer ocrQualityScore;
    String ocrQualityBand;
    String ocrQualitySummary;
    Map<String, Object> ocrStats;
}
