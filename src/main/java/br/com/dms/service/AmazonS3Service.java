package br.com.dms.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Service
public class AmazonS3Service {

    private final AmazonS3 amazonS3;

    private final Environment environment;

    public AmazonS3Service(AmazonS3 amazonS3, Environment environment) {
        this.amazonS3 = amazonS3;
        this.environment = environment;
    }

    public String getBucketName() {
        return this.environment.getProperty("dms.s3.bucket-name");
    }

    public String getPathToDocument(String filenameDms, String cpf, String version) {
        return new StringJoiner(File.separator)
                .add(cpf)
                .add(filenameDms.split(Pattern.quote("."))[0])
                .add(version)
                .add(filenameDms)
                .toString();
    }

    public void deleteDocumentS3(String bucketName, String pathToDocument) {
        amazonS3.deleteObject(bucketName, pathToDocument);
    }

    public String createDocumentS3(String fileName, String cpf, BigDecimal newVersion, ByteArrayInputStream documentData, long contentLength) {
        String bucketName = getBucketName();
        String pathDocument = getPathToDocument(fileName, cpf, String.valueOf(newVersion));
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        amazonS3.putObject(bucketName, pathDocument, documentData, metadata);
        return pathDocument;
    }

    public byte[] getDocumentContentFromS3(String filename, String cpf, String documentVersion) throws IOException {
        String pathDocument = getPathToDocument(filename, cpf, documentVersion);
        return getDocumentContentFromS3(pathDocument);
    }

    public byte[] getDocumentContentFromS3(String pathDocument) throws IOException {
        String bucketName = getBucketName();
        S3Object s3Document = amazonS3.getObject(bucketName, pathDocument);
        return s3Document.getObjectContent().readAllBytes();
    }

    public String copyDocumentS3(String fileName, String cpf, BigDecimal newVersion, BigDecimal oldVersion) {
        String bucketName = getBucketName();
        String pathDocumentNewVersion = getPathToDocument(fileName, cpf, String.valueOf(newVersion));
        String pathDocumentOldVersion = getPathToDocument(fileName, cpf, String.valueOf(oldVersion));
        amazonS3.copyObject(bucketName, pathDocumentOldVersion, bucketName, pathDocumentNewVersion);
        return pathDocumentNewVersion;
    }

    public void deleteAllVersions(String cpf, String filename) {
        var bucketName = getBucketName();
        var objects = amazonS3.listObjects(bucketName, cpf + File.separator + filename.split(Pattern.quote("."))[0] + File.separator);
        while (true) {
            for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
                deleteDocumentS3(bucketName, objectSummary.getKey());
            }
            if (objects.isTruncated()) {
                objects = amazonS3.listNextBatchOfObjects(objects);
            } else {
                break;
            }
        }
    }

    public URL generatePresignedUrl(GeneratePresignedUrlRequest generatePresignedUrlRequest) {
        return amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
    }
}
