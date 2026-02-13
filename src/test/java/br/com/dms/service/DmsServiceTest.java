package br.com.dms.service;

import br.com.dms.config.MongoConfig;
import br.com.dms.controller.request.FinalizeUploadRequest;
import br.com.dms.controller.request.PayloadApprove;
import br.com.dms.controller.request.PayloadUrlPresigned;
import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.core.UploadStatus;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.DocumentWorkflowTransitionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.AmazonS3Service;
import br.com.dms.service.ValidatorCategoryService;
import br.com.dms.service.handler.DocumentCategoryHandler;
import br.com.dms.service.signature.SigningService;
import br.com.dms.service.DocumentValidationService;
import br.com.dms.util.DmsUtil;
import br.com.dms.exception.DmsBusinessException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
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
	private CategoryRepository categoryRepository;

	@MockBean
	private DmsDocumentVersionRepository dmsDocumentVersionRepository;

	@MockBean
	private DmsUtil dmsUtil;

	@MockBean
	private DocumentWorkflowTransitionRepository workflowTransitionRepository;

	@MockBean
	protected SigningService signingService;

	@MockBean
	private ValidatorCategoryService validatorCategoryService;

	@MockBean
	private DocumentValidationService documentValidationService;

	@Autowired
	private Environment environment;

	@Autowired
	private DmsService dmsService;

	private DmsDocument dmsDocument;
	private DmsDocumentVersion dmsDocumentVersion;
	private String uuid;

	@BeforeEach
	void setUp() throws IOException {
		uuid = UUID.randomUUID().toString();

		doNothing().when(documentValidationService).validateAuthor(any(), any());
		doNothing().when(documentValidationService).validateCategory(any(), any());
		doNothing().when(documentValidationService).validateFilename(any(), any());

		when(dmsUtil.getCpfFromMetadata(any())).thenReturn(CPF);
		when(dmsUtil.getBusinessKeyFromMetadata(any(), any())).thenReturn(CPF);
		when(categoryRepository.findByName(any())).thenReturn(Optional.of(Category.builder().name("dr:contrato").uniqueAttributes("cpf").build()));
		HashMap<String, Object> metadata = new HashMap<>();
		metadata.put("cpf", CPF);
		when(dmsUtil.handleObject(any(), any())).thenReturn(metadata);
		when(validatorCategoryService.validateCategory(any(), any(), any(), any())).thenReturn(Set.of());
		when(dmsDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		dmsDocumentVersion = new DmsDocumentVersion();
		dmsDocumentVersion.setId("123");
		dmsDocumentVersion.setFileSize(100L);
		dmsDocumentVersion.setUploadStatus(UploadStatus.COMPLETED);
		dmsDocumentVersion.setVersionNumber(BigDecimal.ONE);

		dmsDocument = new DmsDocument();
		dmsDocument.setId("123");
	}

	@Test
	void testCreateOrUpdate() throws IOException, MimeTypeException {
		when(dmsUtil.validateMimeType(any(), any())).thenReturn(MimeTypes.getDefaultMimeTypes().forName("text/plain"));
		when(dmsDocumentVersionRepository.save(any())).thenReturn(dmsDocumentVersion);

		dmsService.createOrUpdate(uuid, true, "f4btgf23fevrg", null, "DMS", "{}", "dr:contrato", "teste.txt", "comment" );
		verify(validatorCategoryService, times(1)).validateCategory(any(), any(), any(), any());
		verify(dmsUtil, times(1)).getBusinessKeyFromMetadata(any(), any());
		verify(dmsDocumentRepository, times(1)).findByBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(any(), any(), any(), any());
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
		verify(dmsUtil, times(1)).getBusinessKeyFromMetadata(any(), any());
		verify(dmsDocumentRepository, times(1)).findByBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(any(), any(), any(), any());
	}

	@Test
	void testReprove() {
		when(dmsDocumentRepository.findById(any())).thenReturn(Optional.of(dmsDocument));
		when(dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(any(), any())).thenReturn(Optional.of(dmsDocumentVersion));

		dmsService.reprove(uuid, DOCUMENT_ID, DOCUMENT_VERSION);
		verify(dmsDocumentRepository, times(1)).findById(any());
		verify(dmsDocumentVersionRepository, times(1)).findByDmsDocumentIdAndVersionNumber(any(), any());
		verify(dmsDocumentRepository, atLeastOnce()).save(any());
		verify(workflowTransitionRepository, times(1)).save(any());
	}

	@Test
	void testUpdateMetadata() {
		when(dmsUtil.getBusinessKeyFromMetadata(any(), any())).thenReturn(CPF);
		when(dmsDocumentRepository.findByBusinessKeyValueAndFilename(any(), any())).thenReturn(Optional.of(dmsDocument));
		when(dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(any())).thenReturn(Optional.of(dmsDocumentVersion));

		dmsService.updateMetadata(uuid, DOCUMENT_ID, Map.of(), FILENAME);
		verify(dmsUtil, times(1)).getBusinessKeyFromMetadata(any(), any());
		verify(dmsDocumentRepository, times(1)).findByBusinessKeyValueAndFilename(any(), any());
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

	@Test
	void testReviewDocumentWorkflowApprove() {
		when(dmsDocumentRepository.findById(eq(DOCUMENT_ID))).thenReturn(Optional.of(dmsDocument));

		var status = dmsService.reviewDocumentWorkflow(uuid, DOCUMENT_ID, "APPROVE", null, "admin");

		assertEquals(DocumentWorkflowStatus.APPROVED, status);
		verify(workflowTransitionRepository, times(1)).save(any());
	}

	@Test
	void testReviewDocumentWorkflowReproveWithoutReason() {
		when(dmsDocumentRepository.findById(eq(DOCUMENT_ID))).thenReturn(Optional.of(dmsDocument));

		assertThrows(DmsBusinessException.class, () ->
			dmsService.reviewDocumentWorkflow(uuid, DOCUMENT_ID, "REPROVE", null, "admin")
		);
	}

	@Test
	void testFinalizeUploadSuccess() {
		DmsDocumentVersion pendingVersion = new DmsDocumentVersion();
		pendingVersion.setId("version-pending");
		pendingVersion.setDmsDocumentId(DOCUMENT_ID);
		pendingVersion.setVersionNumber(new BigDecimal("2.0"));
		pendingVersion.setPathToDocument("cpf/file/2.0/video.mp4");
		pendingVersion.setUploadStatus(UploadStatus.PENDING);

		when(dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(eq(DOCUMENT_ID), eq("2.0")))
			.thenReturn(Optional.of(pendingVersion));
		when(amazonS3Service.objectExists(pendingVersion.getPathToDocument())).thenReturn(true);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(2048L);
		when(amazonS3Service.getObjectMetadata(pendingVersion.getPathToDocument())).thenReturn(metadata);
		when(dmsDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(dmsDocument));
		when(dmsDocumentRepository.save(any())).thenReturn(dmsDocument);

		FinalizeUploadRequest request = new FinalizeUploadRequest();
		request.setVersion("2.0");
		request.setFileSize(2048L);
		request.setMimeType("video/mp4");

		var response = dmsService.finalizeUpload(uuid, DOCUMENT_ID, request);

		assertEquals("2.0", response.getVersion());
		assertEquals(UploadStatus.COMPLETED, pendingVersion.getUploadStatus());
		assertEquals(2048L, pendingVersion.getFileSize());

		verify(dmsDocumentVersionRepository).save(pendingVersion);
		verify(documentInformationRepository).delete(DOCUMENT_ID, null);
		verify(documentInformationRepository).delete(DOCUMENT_ID, "2.0");
	}

	@Test
	void testFinalizeUploadSizeMismatch() {
		DmsDocumentVersion pendingVersion = new DmsDocumentVersion();
		pendingVersion.setId("version-pending");
		pendingVersion.setDmsDocumentId(DOCUMENT_ID);
		pendingVersion.setVersionNumber(new BigDecimal("3.0"));
		pendingVersion.setPathToDocument("cpf/file/3.0/video.mp4");
		pendingVersion.setUploadStatus(UploadStatus.PENDING);

		when(dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(eq(DOCUMENT_ID), eq("3.0")))
			.thenReturn(Optional.of(pendingVersion));
		when(amazonS3Service.objectExists(pendingVersion.getPathToDocument())).thenReturn(true);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(4096L);
		when(amazonS3Service.getObjectMetadata(pendingVersion.getPathToDocument())).thenReturn(metadata);

		FinalizeUploadRequest request = new FinalizeUploadRequest();
		request.setVersion("3.0");
		request.setFileSize(2048L);

		assertThrows(DmsBusinessException.class, () -> dmsService.finalizeUpload(uuid, DOCUMENT_ID, request));
	}

}
