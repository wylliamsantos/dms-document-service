package br.com.dms.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTypeResponse {
    private String name;
    private String description;
    private Long validityInDays;
    private String requiredAttributes;
}
