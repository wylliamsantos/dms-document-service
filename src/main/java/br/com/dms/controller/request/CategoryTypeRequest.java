package br.com.dms.controller.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTypeRequest {
    private String name;
    private String description;
    private Long validityInDays;
    private String requiredAttributes;
}
