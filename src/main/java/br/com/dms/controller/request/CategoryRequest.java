package br.com.dms.controller.request;

import br.com.dms.domain.core.DocumentGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryRequest {

    @NotNull
    @NotBlank
    private String name;
    private String title;
    private String description;
    private DocumentGroup documentGroup;
    private Long validityInDays;
    private String prefix;
    private String mainType;
    private String typeSearch;
    private String uniqueAttributes;
    private String searchDuplicateCriteria;
    private String path;
    private String site;
    private String parentFolder;
    @NotNull
    private Map<Object, Object> schema;
    private List<CategoryTypeRequest> types;
}
