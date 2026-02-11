package br.com.dms.openapi;

import br.com.dms.config.MongoConfig;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.AmazonS3Service;
import br.com.dms.service.CategoryService;
import br.com.dms.service.DmsService;
import br.com.dms.service.DocumentDeleteService;
import br.com.dms.util.DmsUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecTest {

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
    void generateOpenApiSpec(String apiDocsUrl, @TempDir Path tempDir) {
        File outputFile = tempDir.resolve("openapi.json").toFile();
        gerarSpec(apiDocsUrl, outputFile);

        Assertions.assertTrue(outputFile.exists(), "OpenAPI spec file should be generated");
        Assertions.assertTrue(outputFile.length() > 0, "OpenAPI spec file should not be empty");
    }

    private void gerarSpec(String apiDocsUrl, File outputFile) {
        ResponseEntity<String> response = restTemplate.getForEntity(apiDocsUrl, String.class);

        Assertions.assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode(),
            "OpenAPI endpoint should return HTTP 200");
        Assertions.assertNotNull(response.getBody(), "OpenAPI response body should not be null");

        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(response.getBody());
        } catch (IOException e) {
            Assertions.fail("Erro ao gravar os dados da especificação no arquivo: " + outputFile.getName(), e);
        }
    }
}
