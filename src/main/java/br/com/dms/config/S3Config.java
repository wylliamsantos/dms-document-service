package br.com.dms.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class S3Config {

    @Bean
    public AmazonS3 amazonS3(Environment environment) {
        var isLocal = environment.acceptsProfiles(Profiles.of("local"));

        if (isLocal) {
            return AmazonS3ClientBuilder
                    .standard()
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                            environment.getProperty("dms.s3.endpoint"),
                            environment.getProperty("dms.s3.region")))
                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                            environment.getProperty("dms.s3.access-key"),
                            environment.getProperty("dms.s3.secret-key"))))
                    .withPathStyleAccessEnabled(true)
                    .build();
        }

        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(WebIdentityTokenCredentialsProvider.create())
                .withRegion(Regions.fromName(environment.getProperty("dms.s3.region")))
                .build();
    }
}
