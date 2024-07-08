package com.mail.MailTool.util.ses;

import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.mail.MailTool.model.SESMailMessage;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

public class SESRequestBuilder {

    SESContentBuilder contentBuilder = new SESContentBuilder();
    private String displayName;
    private String emailAddress;

    /**
     * Defines the default values to be used while sending emails.
     *
     * @param displayName  - Name to be displayed as the sender in the "From" section of the email.
     * @param emailAddress - SESEmailMessage Id that will be used to send emails. This has to be verified with AWS.
     */
    public SESRequestBuilder(String displayName, String emailAddress) {
        this.displayName = displayName;
        this.emailAddress = emailAddress;
    }

    /**
     * Creates a properly formatted Email Request object that can be sent using the AWS SES API.
     *
     * @param email        - SESEmailMessage object containing the required information.
     * @param displayName  - Name to be displayed in the "From" section of the email.
     * @param emailAddress - SESEmailMessage Id of the sender. This email Id has to be verified with AWS. This will override the default email Id.
     * @return SendEmailRequest object that has all the required information.
     */
    protected SendEmailRequest createAWSMailRequest(SESMailMessage email, String displayName, String emailAddress) {
        contentBuilder = new SESContentBuilder();
        SendEmailRequest request = new SendEmailRequest();
        request.setDestination(contentBuilder.getDestination(email));
        request.setMessage(contentBuilder.getAWSFormattedMessage(email));
        request.setSource(this.getFormattedAddress(displayName, emailAddress));
        return request;
    }


    /**
     * Creates a properly formatted Email Request object that can be sent using the AWS SES API.
     *
     * @param email        - SESEmailMessage object containing the required information.
     * @param displayName  - Name to be displayed in the "From" section of the email.
     * @param emailAddress - SESEmailMessage Id of the sender. This email Id has to be verified with AWS. This will override the default email Id.
     * @return SendEmailRequest object that has all the required information.
     */
    protected SendEmailRequest createCustomMailRequest(SESMailMessage email, String displayName, String emailAddress) {
        if (emailAddress.equals(this.emailAddress))
            return createAWSMailRequest(email, displayName, emailAddress);

        contentBuilder = new SESContentBuilder();
        SendEmailRequest request = new SendEmailRequest();
        request.setDestination(contentBuilder.getDestination(email));
        request.setMessage(contentBuilder.getAWSFormattedMessage(email, displayName, emailAddress));
        request.setReplyToAddresses(Arrays.asList(this.getFormattedAddress(displayName, emailAddress)));
        request.setSource(this.getFormattedAddress(this.displayName, this.emailAddress));
        return request;
    }

    /**
     * Creates a properly formatted Email Request object that can be sent using the AWS SES API.
     * <p>
     * This message should be used if there are any attachments that need to be sent.
     *
     * @param email        - SESEmailMessage object containing the required information.
     * @param displayName  - Name to be displayed in the "From" section of the email.
     * @param emailAddress - SESEmailMessage Id of the sender. This email Id has to be verified with AWS. This will override the default email Id.
     * @return SendRawEmailRequest object that has all the required information.
     * @throws IOException
     * @throws AddressException
     * @throws MessagingException
     */
    protected SendRawEmailRequest buildAWSRawMessage(SESMailMessage email, String displayName, String emailAddress) throws IOException, MessagingException {
        contentBuilder = new SESContentBuilder();
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message = contentBuilder.addRecipients(message, email);
        message.setSubject(email.getSubject(), "UTF-8");
        message.setFrom(new InternetAddress(emailAddress, displayName));
        message.setContent(contentBuilder.buildMimeBody(email));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        RawMessage rawMessage = new RawMessage(ByteBuffer.wrap(outputStream.toByteArray()));
        return new SendRawEmailRequest(rawMessage);
    }

    private String getFormattedAddress(String name, String email) {
        return "\"" + name + "\" <" + email + ">";
    }
}
