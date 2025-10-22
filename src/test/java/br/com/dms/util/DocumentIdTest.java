package br.com.dms.util;

import br.com.dms.config.MongoConfig;
import br.com.dms.domain.core.DocumentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DocumentIdTest {

    private static final String DOCUMENT_ID = "1234123";

    @MockBean
    private MongoConfig mongoConfig;


    @Test
    void testDocumentId() {
        var documentId = new DocumentId();
        documentId.setVersion("0.14");
        documentId.setId(DOCUMENT_ID);
        var id = documentId.getId();
        assertTrue(DOCUMENT_ID.equals(id));
    }
}
