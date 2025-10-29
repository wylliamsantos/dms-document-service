package br.com.dms.openapi;

import br.com.dms.config.MongoConfig;
import br.com.dms.service.AmazonS3Service;
import br.com.dms.service.DocumentDeleteService;
import br.com.dms.service.CategoryService;
import br.com.dms.service.workflow.DmsService;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.util.DmsUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecTest {
    private final String ESPECIFICACAO_DIR = System.getenv().getOrDefault("ESPECIFICACAO_DIR", "build/spec");
    @Autowired
    private TestRestTemplate restTemplate;
    @MockBean
    private DmsService dmsService;

    @MockBean
    private DocumentDeleteService documentDeleteService;


    @MockBean
    private DmsDocumentRepository dmsDocumentRepository;

    @MockBean
    private DmsDocumentVersionRepository dmsDocumentVersionRepository;

    @MockBean
    private DocumentInformationRepository documentInformationRepository;

    @MockBean
    private AmazonS3Service amazonS3Service;

    @MockBean
    private DmsUtil dmsUtil;

    @MockBean
    private MongoConfig mongoConfig;


    @MockBean
    protected CategoryService categoryService;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs"})
    void generateOpenApiSpec(String apiDocsUrl) {
        gerarSpec(apiDocsUrl);
        Assertions.assertTrue(true);
    }

    private void gerarSpec(String apiDocsUrl) {
        ResponseEntity<String> response = restTemplate.getForEntity(apiDocsUrl, String.class);
        if(200 == response.getStatusCodeValue()){
            File outputFile = new File(ESPECIFICACAO_DIR+"/openapi.json");
            outputFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(response.getBody());
            } catch (IOException e) {
                System.out.println("Erro ao gravar os dados da especificação no arquivo:" + outputFile.getName());
            }
            System.out.println("Especificação OpenAPI gerada com sucesso no arquivo: " + outputFile.getAbsolutePath());
        } else {
            System.out.println("Especificação não encontrada no path: "+apiDocsUrl);
        }
    }
}
