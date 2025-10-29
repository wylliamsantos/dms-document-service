package br.com.dms.util;

import br.com.dms.config.MongoConfig;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.exception.DmsBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.tika.mime.MimeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DmsUtilTest {

	private final String CPF = "11111111111";

	private Map<String, Object> metadados;

	@MockBean
	private MongoConfig mongoConfig;


	@Value(value = "${dms.uid}")
	private String uid;

	@Autowired
	private DmsUtil dmsUtil;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Environment environment;

	@BeforeAll
	void before() {
		metadados = Map.of("ac:cpf", CPF);
	}

	@Test
	void testGenerateNewMajorVersion() {
		BigDecimal newMajorVersion = dmsUtil.generateNewMajorVersion(BigDecimal.ONE);
		assertEquals(new BigDecimal("2.0"), newMajorVersion);
	}

	@Test
	void testGenerateVersion() {
		BigDecimal newMajorVersion = dmsUtil.generateVersion(false, BigDecimal.ONE);
		assertEquals(new BigDecimal("1.1"), newMajorVersion);
	}

	@Test
	void testHandleObject() {
		HashMap<String, Object> map = dmsUtil.handleObject(UUID.randomUUID().toString(), "{}");
		assertNotNull(map);
	}

	@Test
	void testHandleObjectFailure() {
		Exception exception = assertThrows(DmsBusinessException.class, () -> dmsUtil.handleObject(UUID.randomUUID().toString(), (String)null));
		String message = exception.getMessage();
		assertTrue(message.contains(environment.getProperty("dms.msg.jsonMetadataError")));
	}

	@Test
	void testGetCpfFromMetadata() {
		String cpf = dmsUtil.getCpfFromMetadata(metadados);
		assertEquals(CPF, cpf);
	}

	@Test
	void testValidateMimeType(@TempDir Path tempDir) throws IOException {
		Path numbers = tempDir.resolve("empty.pdf");

		PDDocument document = new PDDocument();
		PDPage blankPage = new PDPage();
		document.addPage( blankPage );
		document.save(numbers.toFile());
		MimeType mime = dmsUtil.validateMimeType(UUID.randomUUID().toString(), new ByteArrayInputStream(FileUtils.readFileToByteArray(numbers.toFile())));
		document.close();
		assertTrue(APPLICATION_PDF_VALUE.equals(mime.getName()));
	}

	@Test
	void testValidateMimeType_wrong() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("test.txt"));
			zos.write("zip-content".getBytes());
			zos.closeEntry();
		}

		Exception exception = assertThrows(DmsBusinessException.class, () ->
				dmsUtil.validateMimeType(
					UUID.randomUUID().toString(),
					new ByteArrayInputStream(baos.toByteArray())
				));

		String message = exception.getMessage();
		assertTrue(message.contains(environment.getProperty("dms.msg.mimeTypeInvalid")));
	}

	@Test
	void getCpfKeyRequiredDefault() {
		Map<Object, Object> schema = Map.of("properties", "properties");
		String cpf = dmsUtil.getCpfKeyRequired(Category.builder().schema(schema).build());
		assertEquals("cpf", cpf);
	}

	@Test
	void getCpfKeyRequired() {
		Map<Object, Object> schema = Map.of("required", Arrays.asList("ac:cpf", "ac:numeroIdentificacao"));
		String cpf = dmsUtil.getCpfKeyRequired(Category.builder().schema(schema).build());
		assertEquals("ac:cpf", cpf);
	}

	@Test
	void testGetCpfFromMetadataWithRequiredCpfField() {
		String cpf = dmsUtil.getCpfFromMetadata(metadados, "ac:cpf");
		assertEquals(CPF, cpf);
	}

}
