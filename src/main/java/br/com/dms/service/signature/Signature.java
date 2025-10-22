package br.com.dms.service.signature;


import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPException;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

public class Signature implements SignatureInterface {

    private PrivateKey privateKey;
    private List<X509Certificate> certificateChain;
    private String tsaUrl;
    private int connectTimeout;

    Signature(List<X509Certificate> certificates, PrivateKey privateKey, String tsaUrl, int connectTimeout) throws IOException {
        this.certificateChain = Optional.ofNullable(certificates)
                .orElseThrow(() -> (new IOException("Could not find a proper certificate chain")));

        this.privateKey = Optional.ofNullable(privateKey)
                .orElseThrow(() -> (new IOException("Could not find a privateKey")));

        this.tsaUrl = tsaUrl;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {

            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            X509Certificate cert = this.certificateChain.get(0);
            ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA256WithRSA").build(this.privateKey);
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(sha1Signer, cert));
            gen.addCertificates(new JcaCertStore(this.certificateChain));
            CMSProcessableInputStream msg = new CMSProcessableInputStream(content);
            CMSSignedData signedData = gen.generate(msg, false);

            if (tsaUrl != null) {
                TimeStampManager timeStampManager = new TimeStampManager(tsaUrl, connectTimeout);
                signedData = timeStampManager.addSignedTimeStamp(signedData);
            }

            return signedData.getEncoded();
        } catch (GeneralSecurityException | CMSException | OperatorCreationException | TSPException e) {
            throw new IOException(e);
        }
    }
}