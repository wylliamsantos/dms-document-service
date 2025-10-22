package br.com.dms.service.signature;

import br.com.dms.util.PDFUtil;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.tika.mime.MimeType;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
public class SigningService {

    private Logger logger = LoggerFactory.getLogger(SigningService.class);

    private static final String PDF_TYPE = "application/pdf";
    private static final String PDF_EXTENSION = ".pdf";

    private final String tsaUrl;
    private final int connectTimeout;

    private final String privateKeyBase64;

    private final String certificateBase64;

    private final boolean allowDigitalSignature;

    private final PDFUtil pdfUtil;

    public SigningService(@Value("${timestamp-authority.url}") String tsaUrl,
                          @Value("${timestamp-authority.connectTimeout}") int connectTimeout,
                          @Value("${signature.private-key}") String privateKeyBase64,
                          @Value("${signature.certificate}") String certificateBase64,
                          @Value("${allowDigitalSignature}") boolean allowDigitalSignature,
                          PDFUtil pdfUtil) {
        this.tsaUrl = tsaUrl;
        this.connectTimeout = connectTimeout;
        this.privateKeyBase64 = privateKeyBase64;
        this.certificateBase64 = certificateBase64;
        this.allowDigitalSignature = allowDigitalSignature;
        this.pdfUtil = pdfUtil;
    }

    public ByteArrayResource signSignaturePdf(byte[] bytes, String signatureText) throws IOException {
        File pdfFile = File.createTempFile(UUID.randomUUID().toString(), ".pdf");
        FileUtils.writeByteArrayToFile(pdfFile, bytes);

        File signedPdf = File.createTempFile(UUID.randomUUID().toString(), ".pdf");
        writeSignatureText(pdfFile, signedPdf, signatureText);

        byte[] signedPdfBytes = Files.readAllBytes(signedPdf.toPath());

        pdfFile.delete();
        signedPdf.delete();

        return new ByteArrayResource(signedPdfBytes) {
            @Override
            public String getFilename() {
                return "teste";
            }
        };
    }

    private void writeSignatureText(File inFile, File outFile, String signatureText) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outFile);
             PDDocument doc = PDDocument.load(inFile)) {
            int lastPage = doc.getDocumentCatalog().getPages().getCount() - 1;
            PDPage page = doc.getPage(lastPage);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, false)) {
                pdfUtil.addText(signatureText, PDType1Font.TIMES_ROMAN, 12, contentStream, page);
            }
            doc.getDocument().getTrailer().removeItem(COSName.TYPE);
            pdfUtil.markUpdatedElements(doc, page);
            doc.saveIncremental(fos);
        }
    }


    public ByteArrayResource signPdf(String filenameDms, ByteArrayResource pdfToSign) {
        logger.info("signPdf: Iniciado processo assinar pdf, filename {}", filenameDms);

        try {
            PrivateKey privateKey = getPrivateKey();
            List<X509Certificate> certificates = getCertificate();
            Signature signature = new Signature(certificates, privateKey, tsaUrl, connectTimeout);
            File pdfFile = File.createTempFile(UUID.randomUUID().toString(), PDF_EXTENSION);
            FileUtils.writeByteArrayToFile(pdfFile, pdfToSign.getByteArray());

            File signedPdf = File.createTempFile(UUID.randomUUID().toString(), PDF_EXTENSION);
            this.signDetached(signature, pdfFile, signedPdf);

            byte[] signedPdfBytes = Files.readAllBytes(signedPdf.toPath());

            pdfFile.delete();
            signedPdf.delete();

            logger.info("signPdf: Fim processo assinar pdf, filename {}", filenameDms);

            return new ByteArrayResource(signedPdfBytes) {
                @Override
                public String getFilename() {
                    return filenameDms;
                }
            };
        } catch (Exception e) {
            logger.error("signPdf: Error to sign pdf", e);
        }

        return pdfToSign;
    }

    private List<X509Certificate> getCertificate() {
        try {
            List<X509Certificate> certificateChain = new ArrayList<>();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> c = cf.generateCertificates(new ByteArrayInputStream(Base64.getDecoder().decode(certificateBase64)));
            for (Object o : c) {
                certificateChain.add((X509Certificate) o);
            }

            return certificateChain;
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getPrivateKey() throws IOException {
        try (PemReader pemReader = new PemReader(new InputStreamReader(new ByteArrayInputStream(Base64.getDecoder().decode(privateKeyBase64))))) {
            PemObject privateKeyObject = pemReader.readPemObject();
            RSAPrivateCrtKeyParameters privateKeyParameter;
            privateKeyParameter = (RSAPrivateCrtKeyParameters) PrivateKeyFactory.createKey(privateKeyObject.getContent());
            return new JcaPEMKeyConverter().getPrivateKey(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameter));
        }
    }

    private void signDetached(SignatureInterface signature, File inFile, File outFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outFile);
             PDDocument doc = PDDocument.load(inFile)) {

            PDSignature pdSignature = new PDSignature();
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);

            doc.getDocument().getTrailer().removeItem(COSName.TYPE);
            pdSignature.setSignDate(Calendar.getInstance());
            doc.addSignature(pdSignature, signature);
            doc.saveIncremental(fos);
        }
    }

    public boolean applyDigitalSignature(MimeType mimeType) {
        return allowDigitalSignature && mimeType.toString().equals(PDF_TYPE);
    }
}
