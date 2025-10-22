package br.com.dms.service;

import br.com.dms.config.MongoConfig;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AmazonS3ServiceTest {

	public static final String PATH_TO_DOCUMENT = "/111/111/111";
	private final String CPF = "11111111111";

	@MockBean
	private AmazonS3 amazonS3;

	@Autowired
	private Environment environment;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AmazonS3Service amazonS3Service;

	@MockBean
	private MongoConfig mongoConfig;


	@Test
	void testGetBucketName() {
		var bucketName = amazonS3Service.getBucketName();
		var property = this.environment.getProperty("dms.s3.bucket-name");
		assertEquals(bucketName, property);
	}

	@Test
	void testGetPathToDocument() {
		var path = amazonS3Service.getPathToDocument("testePath.txt", CPF, "1.0");
		var expected = CPF + File.separator + "testePath" + File.separator + "1.0" + File.separator + "testePath.txt";
		assertEquals(expected, path);
	}

	@Test
	void testDeleteDocumentS3() {
		amazonS3Service.deleteDocumentS3(amazonS3Service.getBucketName(),  PATH_TO_DOCUMENT);
		verify(amazonS3,times(1)).deleteObject(amazonS3Service.getBucketName(), PATH_TO_DOCUMENT);
	}

}
