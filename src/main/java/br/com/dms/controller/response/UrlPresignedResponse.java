package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URL;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UrlPresignedResponse {

    private DocumentId id;
    private URL url;
}
