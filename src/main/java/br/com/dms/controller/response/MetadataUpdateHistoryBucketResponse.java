package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataUpdateHistoryBucketResponse {
    private String key;
    private long count;
}
