package br.com.dms.domain.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryType {
    private String name;
    private String description;
    private Long validityInDays;
    private String requiredAttributes;
}
