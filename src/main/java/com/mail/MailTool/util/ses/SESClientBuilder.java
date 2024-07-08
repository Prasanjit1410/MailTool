package com.mail.MailTool.util.ses;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class creates a builder for the AWS SES Client.
 *
 * @author WUE
 */
public class SESClientBuilder {
    final static Logger logger = LoggerFactory.getLogger(SESClientBuilder.class);
    SESRequestBuilder requestBuilder = null;
    private String defaultDisplayName;
    private String defaultEmailAddress;
    private Regions defaultRegion;
    private String accessKeyId;
    private String secretAccessKey;
    AmazonSimpleEmailService client = null;

    /**
     * Defines the configuration parameters required for the AWS SES Client.
     *
     * @param displayName     Name to be displayed in the From section of the email.
     * @param emailAddress    Email address to be displayed as the from email. This has to be a verified email with AWS.
     * @param region          AWS Server region used to send emails using AWS SES.
     * @param accessKeyId     AWS access key Id. This can be obtained from the AWS SES Console.
     * @param secretAccessKey AWS Secret Access Key. This can be obtained from the AWS SES Console.
     */
    public SESClientBuilder(String displayName, String emailAddress, Regions region, String accessKeyId, String secretAccessKey) {
        this.defaultDisplayName = displayName;
        this.defaultEmailAddress = emailAddress;
        this.defaultRegion = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        createDefaultCredentials();
        requestBuilder = new SESRequestBuilder(displayName, emailAddress);
    }

    private void createDefaultCredentials() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(this.accessKeyId, this.secretAccessKey);
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        client = AmazonSimpleEmailServiceClientBuilder.standard().withRegion(defaultRegion).withCredentials(credentialsProvider).build();
    }

    /**
     * Sends an email using the AWS SES API.
     *
     * @param email SESEmailMessage object containing the required information.
     * @return true if the email was sent successfully; false otherwise
     */
    public boolean sendEmail(SESMailMessage email) {
        if ((null != email.getFileList()) && (email.getFileList().size() > 0)) {
            try {
                SendRawEmailRequest rawEmailRequest = requestBuilder.
                        buildAWSRawMessage(email, defaultDisplayName, defaultEmailAddress);
                client.sendRawEmail(rawEmailRequest);
                return true;
            } catch (Exception e) {
                logger.error("Error {}", e);
                logger.error("SESEmailMessage Sending Failed! Error: " + e.getMessage());
                return false;
            }
        }
        try {
            SendEmailRequest request = requestBuilder.createCustomMailRequest(email, defaultDisplayName, defaultEmailAddress);
            client.sendEmail(request);
            return true;
        } catch (Exception ex) {
            logger.error("SESEmailMessage Sending Failed for email {}! Error: {}", email.getTo(), ex.getMessage());
            return false;
        }
    }

}
