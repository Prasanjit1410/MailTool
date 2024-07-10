package com.mail.MailTool.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.mail.MailTool.util.ses.SESClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AWSConfig {

    @Value("${SECRET_KEY}")
    private String secretKey;

    @Value("${ACCESS_KEY}")
    private String accessKey;

    @Value("${REGION}")
    private String region;

    @Value("${DISPLAY_NAME}")
    private String displayName;

    @Value("${FROM_EMAIL}")
    public String fromEmail;

    @Bean
    public SESClientBuilder build() {
        Regions region = Regions.fromName(this.region);
        SESClientBuilder clientBuilder = new SESClientBuilder(displayName, fromEmail, region, accessKey, secretKey);
        return clientBuilder;
    }
    @Bean
    public AmazonSimpleEmailService credentials() {
        AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(region).build();
        return client;
    }

    @Bean
    public AmazonS3 s3client() {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey,secretKey)))
                .build();
        return s3Client;
    }


}
