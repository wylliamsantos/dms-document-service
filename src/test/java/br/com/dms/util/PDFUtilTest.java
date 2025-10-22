package br.com.dms.util;

import br.com.dms.config.MongoConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;

import java.io.IOException;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PDFUtilTest {

    private static final String TEXTO = "TEXTO";

    @MockBean
    private MongoConfig mongoConfig;


    @Autowired
    private PDFUtil pdfUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    private PDDocument document;
    private PDPage page;
    private PDFont font;
    private PDPageContentStream stream;

    @BeforeAll
    public void setUp() throws Exception {
        document = new PDDocument();
        page = new PDPage();
        page.setResources(new PDResources());
        document.addPage(page);
        font = Mockito.mock(PDFont.class);
        stream = Mockito.mock(PDPageContentStream.class);

        Mockito.when(font.getStringWidth(anyString())).thenAnswer(invocation -> ((String) invocation.getArgument(0)).length() * 400f);
        Mockito.when(font.getCOSObject()).thenReturn(new COSDictionary());
    }

    @AfterAll
    void tearDown() throws Exception {
        document.close();
    }

    @Test
    void testAddText() throws Exception {
        pdfUtil.addText(TEXTO, font, 14, stream, page);
    }

    @Test
    void testAddText_bigSize() throws Exception {
        pdfUtil.addText(TEXTO, font, 100, stream, page);
    }

    @Test
    void testAddMultilineText() throws Exception {
        pdfUtil.addMultilineText(TEXTO, font, 14, stream, 2.0f);
    }

    @Test
    void testMarkUpdatedElements() throws Exception {
        pdfUtil.markUpdatedElements(document, page);
    }

    @Test
    public void testMarkUpdatedElementss() throws  IOException {

        // Criar a página PDPage
        PDDocument document1 = new PDDocument();
        PDPage page1 = new PDPage();
        page1.setResources(new PDResources());
        PDPageContentStream stream1 = Mockito.mock(PDPageContentStream.class);
        pdfUtil.addText(TEXTO, font, 14, stream1, page1);
        pdfUtil.addText(TEXTO, font, 100, stream1, page1);
        pdfUtil.addMultilineText(TEXTO, font, 14, stream1, 2.0f);

        // Acessar o objeto COSDictionary do PDPage
        COSDictionary dict = page1.getCOSObject();

        // Criar um novo COSDictionary que servirá como "pai"
        COSDictionary parentDict = new COSDictionary();

        // Adicionar o parentDict ao dict com a chave COSName.PARENT
        dict.setItem(COSName.PARENT, parentDict);

        pdfUtil.markUpdatedElements(document1, page1);

        document1.close();
    }

    @Test
    void testAddText2() throws Exception {
        pdfUtil.addText("TEXTO QUE SERA ADICIONAO AO PDF", font, 200, stream, page);
    }
}
