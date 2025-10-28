package br.com.dms.domain.mongodb;

import br.com.dms.domain.core.DocumentGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

@Document(collection = "documentCategory")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Category {
    @Id
    private String id;
    private String name;
    private String title;
    private String description;
    @Field("document_group")
    private DocumentGroup documentGroup;
    private String prefix;
    @Field("validity_in_days")
    private Long validityInDays;
    @Field("main_type")
    private String mainType;
    @Field("type_search")
    private String typeSearch;
    @Field("unique_attributes")
    private String uniqueAttributes;
    private Map<Object, Object> schema;
    private List<CategoryType> types;
}
