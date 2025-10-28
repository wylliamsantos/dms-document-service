package br.com.dms.service.workflow;

import br.com.dms.config.MongoConfig;
import br.com.dms.controller.request.PayloadApprove;
import br.com.dms.controller.request.PayloadUrlPresigned;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.AmazonS3Service;
import br.com.dms.service.ValidatorCategoryService;
import br.com.dms.service.handler.DocumentCategoryHandler;
import br.com.dms.service.signature.SigningService;
import br.com.dms.util.DmsUtil;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DmsServiceTest {

	private static final String DOCUMENT_ID = "12357491243";
	private static final String DOCUMENT_VERSION = "55.0";
	private static final String FILENAME = "FILENAME.TXT";
	private static final String CPF = "11111111111";

	@MockBean
	private MongoConfig mongoConfig;


	@MockBean
	private AmazonS3Service amazonS3Service;

	@MockBean
	private DocumentCategoryHandler documentCategoryHandler;

	@MockBean
	private DocumentInformationRepository documentInformationRepository;

	@MockBean
	private DmsDocumentRepository dmsDocumentRepository;

	@MockBean
	private DmsDocumentVersionRepository dmsDocumentVersionRepository;

	@MockBean
	private DmsUtil dmsUtil;

	@MockBean
	protected SigningService signingService;

	@MockBean
	private ValidatorCategoryService validatorCategoryService;

	@Autowired
	private Environment environment;

	@Autowired
	private DmsService dmsService;

	private DmsDocument dmsDocument;
	private DmsDocumentVersion dmsDocumentVersion;
	private String uuid;

	@BeforeAll
	void setUp() {
		uuid = UUID.randomUUID().toString();

		dmsDocumentVersion = new DmsDocumentVersion();
		dmsDocumentVersion.setId("123");
		dmsDocumentVersion.setFileSize(100L);

		dmsDocument = new DmsDocument();
		dmsDocument.setId("123");
	}

	@Test
	void testCreateOrUpdate() throws IOException, MimeTypeException {
		when(dmsUtil.validateMimeType(any(), any())).thenReturn(MimeTypes.getDefaultMimeTypes().forName("text/plain"));
		when(dmsDocumentVersionRepository.save(any())).thenReturn(dmsDocumentVersion);

		dmsService.createOrUpdate(uuid, true, "f4btgf23fevrg", null, "DMS", "{}", "dr:contrato", null,  "teste.txt", "comment" );
		verify(validatorCategoryService, times(1)).validateCategory(any(), any(), any(), any());
		verify(dmsUtil, times(1)).getCpfFromMetadata(any());
		verify(dmsDocumentRepository, times(1)).findByCpfAndFilename(any(), any());
	}

	@Test
	void testGeneratePresignedUrl() throws IOException, MimeTypeException {
		when(dmsDocumentVersionRepository.save(any())).thenReturn(dmsDocumentVersion);

		var presigned = new PayloadUrlPresigned();
		presigned.setFileName("teste");
		presigned.setAuthor("teste");
		presigned.setCategory("ac:identificacao");
		presigned.setComment("teste");
		presigned.setFileSize(6000l);
		presigned.setIsFinal(true);
		presigned.setMimeType("video/mpeg");
		presigned.setMetadata("{\\\"ac:numeroDocumento\\\":\\\"11111111111\\\",\\\"ac:idProcesso\\\":\\\"123123\\\",\\\"ac:cpf\\\":\\\"111111111111\\\"}");

		dmsService.generatePresignedUrl(uuid, presigned);
		verify(validatorCategoryService, times(1)).validateCategory(any(), any(), any(), any());
		verify(dmsUtil, times(1)).getCpfFromMetadata(any());
		verify(dmsDocumentRepository, times(1)).findByCpfAndFilename(any(), any());
	}

	@Test
	void testReprove() {
		when(dmsDocumentRepository.existsById(any())).thenReturn(true);
		when(dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(any(), any())).thenReturn(Optional.of(dmsDocumentVersion));

		dmsService.reprove(uuid, DOCUMENT_ID, DOCUMENT_VERSION);
		verify(dmsDocumentRepository, times(1)).existsById(any());
		verify(dmsDocumentVersionRepository, times(1)).findByDmsDocumentIdAndVersionNumber(any(), any());
		verify(dmsDocumentVersionRepository, times(1)).delete(any());
		verify(amazonS3Service, times(1)).deleteDocumentS3(any(), any());
	}

	@Test
	void testUpdateMetadata() {
		when(dmsUtil.getCpfFromMetadata(any())).thenReturn(CPF);
		when(dmsDocumentRepository.findByCpfAndFilename(any(), any())).thenReturn(Optional.of(dmsDocument));
		when(dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(any())).thenReturn(Optional.of(dmsDocumentVersion));

		dmsService.updateMetadata(uuid, DOCUMENT_ID, Map.of(), FILENAME);
		verify(dmsUtil, times(1)).getCpfFromMetadata(any());
		verify(dmsDocumentRepository, times(1)).findByCpfAndFilename(any(), any());
	}

	@Test
	void testApproveWithSignatureText() throws IOException {
		when(dmsDocumentRepository.findById(any())).thenReturn(Optional.of(dmsDocument));
		when(dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(any())).thenReturn(Optional.of(dmsDocumentVersion));
		when(dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(any(), any())).thenReturn(Optional.of(dmsDocumentVersion));
		when(dmsUtil.generateNewMajorVersion(any())).thenReturn(BigDecimal.ONE);

		dmsService.approveWithSignatureText(DOCUMENT_ID, DOCUMENT_VERSION, uuid, new PayloadApprove());

		verify(dmsDocumentRepository, times(1)).findById(any());
		verify(dmsDocumentVersionRepository, times(1)).findLastVersionByDmsDocumentId(any());
		verify(dmsDocumentVersionRepository, times(1)).findByDmsDocumentIdAndVersionNumber(any(), any());
		verify(dmsUtil, times(1)).generateNewMajorVersion(any());
	}

}
