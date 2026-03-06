package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MetadataUpdateHistoryPageResponse {
    List<MetadataUpdateHistoryEntryResponse> content;
    long totalElements;
    int number;
    int size;
}
