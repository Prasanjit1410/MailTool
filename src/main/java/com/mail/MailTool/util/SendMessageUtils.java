package com.mail.MailTool.util;


import javax.mail.MessagingException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.mail.Message.RecipientType;


import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.mail.MailTool.domain.mail.*;
import com.mail.MailTool.repository.mail.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import com.amazonaws.services.simpleemail.model.RawMessage;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


import com.mail.MailTool.config.AWSConfig;
import com.mail.MailTool.model.MailContent;
import com.mail.MailTool.util.ses.SESMailMessage;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class SendMessageUtils {
    public final static Logger LOGGER = LoggerFactory.getLogger(SendMessageUtils.class);


    private static final String UNSUBSCRIBE_BASE_URL = "https://wuelev8.tech/";

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    UnsubscribedMailRepository unsubscribedMailsRepository;

    @Autowired
    MailStatusRepository mailStatusRepository;

    @Autowired
    BlockedEmailRepository blockedEmailRepository;

    @Autowired
    private AWSConfig awsConfig;

    @Autowired
    SentMailStatsRepository sentMailStatsRepository;

    @Autowired
    PartnerMailLimitRepository partnerMailLimitRepository;
    @Autowired
    BulkMailAttemptRepository bulkMailAttemptRepository;

    @Value("${FROM_EMAIL:no-reply@wuelev8.tech}")
    private String fromEmail;

    @Value("${DISPLAY_NAME:Where U Elevate Team}")
    private String displayName;

    @Value("${REPLYTO_EMAIL:care@wuelev8.tech}")
    private String replyToEmail;

    @Value("${domain.url:}")
    private String domainUrl;

    @Value("${bucket}")
    private String bucket;

    @Value("${config_set:configset}")
    private String configSet;

    @Async
    public String sendMail(String to, MailContent emailContent) throws MessagingException {
        try {
            log.debug("Send mail config {} ::: {}", awsConfig, emailContent.getTemplateName());
            Context context = new Context();
            context.setVariable("content", emailContent);
            String process = templateEngine.process(emailContent.getTemplateName(), context);
            String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");

            //SENDING EMAIL VIA SES
            SESMailMessage sesEmailMessage = new SESMailMessage();
            sesEmailMessage.setTo(to);
            sesEmailMessage.setBody(data);
            sesEmailMessage.setSubject(emailContent.getSubject());
            if(!StringUtils.isEmpty(emailContent.getBcc())){
                sesEmailMessage.setBcc(emailContent.getBcc());
            }
            if(!StringUtils.isEmpty(emailContent.getCc())){
                sesEmailMessage.setCc(emailContent.getCc());
            }
            awsConfig.build().sendEmail(sesEmailMessage);
            return "Sent";
        }
        catch (Exception e) {
            log.error("exception {}", e);
        }
        return "sent";
    }
    public String sendMailWithCustomHeader(MailContent emailContent, String to, String headerValue, String headerName) {
        try {
            log.debug("Send mail config {} ::: {}", awsConfig, emailContent.getTemplateName());
            Context context = new Context();
            context.setVariable("content", emailContent);
            String process = templateEngine.process(emailContent.getTemplateName(), context);
            String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");

            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(data, "text/html");


            Properties props = System.getProperties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage message = new MimeMessage(session);
            message.addHeader(headerName, headerValue);
            // Set the content of the message
            message.setSubject(emailContent.getSubject());

            // Create the message body
            MimeMultipart multipart = new MimeMultipart();

            // Add the text message part
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(emailContent.getMessage(), "text/html");
            multipart.addBodyPart(textPart);

            // Add the email body part to the multipart
            multipart.addBodyPart(bodyPart);

            if (emailContent.getCc() != null) {
                String cc = emailContent.getCc().toString();
                if (isValidEmail(cc)) {
                    message.setRecipients(RecipientType.CC, InternetAddress.parse(cc));
                }
            }
            if (emailContent.getBcc() != null) {
                String bcc = emailContent.getBcc().toString();
                if (isValidEmail(bcc)) {
                    message.setRecipients(RecipientType.BCC, InternetAddress.parse(bcc));
                }
            }

            // Encode the message as a Base64 string
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            byte[] rawEmail = outputStream.toByteArray();
            String newEncodedEmails = Base64.getEncoder().encodeToString(rawEmail);

            SendRawEmailRequest request = new SendRawEmailRequest()
                    .withSource(fromEmail)
                    .withDestinations(to)
                    .withConfigurationSetName(configSet)
                    .withRawMessage(new RawMessage().withData(ByteBuffer.wrap(Base64.getMimeDecoder().decode(newEncodedEmails))));


            SendRawEmailResult result = awsConfig.credentials().sendRawEmail(request);
            return ("Email sent to " + to);
        } catch (Exception ex) {
            return ("The email was not sent. Error message: " + ex.getMessage());
        }
    }
    public static boolean isValidEmail(String email) {
        String regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
        return email.matches(regex);
    }
    @Async
    public CompletableFuture<String> sendMailWithCustomHeaderAndAttachment(MailContent emailContent, String to, List<String> attachmentPaths) {
        try {
            log.info("Send mail config {} ::: {}", awsConfig, emailContent, emailContent.getTemplateName());
            Context context = new Context();
            List<String> validEmails = new ArrayList<>();
            String senderEmail = "";
            String senderDisplayName = "";
            String senderReplyToEmail = "";
            List<String> unsubscribedEmails = unsubscribedMailsRepository.findAll().stream()
                    .map(UnsubscribedMails::getEmailId)
                    .collect(Collectors.toList());

            List<String> blockedBouncedEmails = mailStatusRepository.findByStatus("Bounce").stream()
                    .map(MailStatus::getReceiverEmail)
                    .collect(Collectors.toList());

            blockedBouncedEmails.addAll(unsubscribedEmails);
            Set<String> allBlockedEmails = new HashSet<>(blockedBouncedEmails);

            String[] toEmails = to.split(",");

            String data = replaceTemplateWithInlineCss(emailContent.getMessage());

            emailContent.setMessage(data);

            Map<String, String> customerDetails = new HashMap<>();
            if (emailContent.getCustomerDetails() != null) {
                customerDetails.putAll(emailContent.getCustomerDetails());

                if (!StringUtils.isEmpty(customerDetails.get("from"))) {
                    senderEmail = customerDetails.get("from");
                }

                if (!StringUtils.isEmpty(customerDetails.get("displayName"))) {
                    senderDisplayName = customerDetails.get("displayName");
                }

                senderReplyToEmail = senderEmail;
                emailContent.setTemplateName("GenericMailForCustomers");
                JSONObject socialLinks = new JSONObject(customerDetails.get("socialLinks"));
                setSocialLinks(emailContent, socialLinks);

                log.info("from email ::: {} ::: {}", fromEmail);
            } else {
                senderEmail = fromEmail;
                senderDisplayName = displayName;
                senderReplyToEmail = replyToEmail;
            }

            log.info("Sender email ::: {} ::: sender display name ::: {} ::: reply to email ::: {}",
                    senderEmail, senderDisplayName, senderReplyToEmail);

            List<String> emailSuccessfullySent = new ArrayList<>();
            List<String> blockedEmail = new ArrayList<>();

            for (String email : toEmails) {
                try {
                    if(!isValidEmail(email.trim())){
                        log.warn("Not a valid email ::: {}", email);
                        blockedEmail.add("[INVALID]"+email);
                        continue;
                    }

                    if (allBlockedEmails.contains(email.trim())) {
                        log.info("Skipping email: {} - Blocked", email.trim());
                        blockedEmail.add("[BLOCKED]"+email.trim());
                        continue;
                    }



                    String encodedEmail = Base64.getEncoder().encodeToString(email.trim().getBytes());
                    String unsubscribeLink = domainUrl + "/unsubscribe?id=" + encodedEmail;
                    log.debug("unsubscribe link for email {} ::: {}", email, unsubscribeLink);
                    emailContent.setUnsubscribeLink(unsubscribeLink);
                    context.setVariable("content", emailContent);
                    String process = templateEngine.process(emailContent.getTemplateName(), context);
                    //String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");
                    MimeBodyPart bodyPart = new MimeBodyPart();
                    bodyPart.setContent(process, "text/html; charset=UTF-8");

                    Properties props = System.getProperties();
                    Session session = Session.getDefaultInstance(props, null);
                    MimeMessage message = new MimeMessage(session);
                    message.addHeader(emailContent.getHeaderIdName(), emailContent.getHeaderIdValue());
                    message.setSubject(emailContent.getSubject());

                    MimeMultipart multipart = new MimeMultipart();
                    multipart.addBodyPart(bodyPart);

                    if (attachmentPaths!=null && !attachmentPaths.isEmpty()) {
                        log.info("attachment paths not empty ::: {}", attachmentPaths);
                        for (String attachmentPath : attachmentPaths) {
                            MimeBodyPart attachmentPart = new MimeBodyPart();
                            DataSource attachment;
                            String fileName;

                            if (isS3Link(attachmentPath)) {
                                log.info("s3 link");
                                String[] parts = attachmentPath.substring(5).split("/");
                                log.info("parts ::: {}", (Object) parts);
                                String bucketName = parts[2].substring(0, parts[2].indexOf(".s3"));
                                log.info("bucket name ::: {}", bucketName);
                                String key = parts[parts.length - 1];
                                log.info("key ::: {}", key);
                                S3Object s3Object = awsConfig.s3client().getObject(new GetObjectRequest(bucketName, key));
                                attachment = new ByteArrayDataSource(s3Object.getObjectContent(), s3Object.getObjectMetadata().getContentType());
                                fileName = parts[parts.length - 1];
                            } else {
                                log.info("local link");
                                File file = new File(attachmentPath);
                                attachment = new FileDataSource(file);
                                fileName = file.getName();
                            }
                            attachmentPart.setDataHandler(new DataHandler(attachment));
                            attachmentPart.setFileName(fileName);
                            multipart.addBodyPart(attachmentPart);
                        }
                    }
                    message.setContent(multipart);

                    if (emailContent.getCc() != null && isValidEmail(emailContent.getCc().toString())) {
                        message.setRecipients(RecipientType.CC, InternetAddress.parse(emailContent.getCc().toString()));
                    }

                    if (emailContent.getBcc() != null && isValidEmail(emailContent.getBcc().toString())) {
                        message.setRecipients(RecipientType.BCC, InternetAddress.parse(emailContent.getBcc().toString()));
                    }

                    message.setFrom(new InternetAddress(senderEmail, senderDisplayName));
                    message.setReplyTo(new javax.mail.Address[]{
                            new javax.mail.internet.InternetAddress(senderReplyToEmail)
                    });

                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        message.writeTo(outputStream);
                        byte[] rawEmail = outputStream.toByteArray();
                        String encodedEmails = Base64.getEncoder().encodeToString(rawEmail);

                        SendRawEmailRequest request = new SendRawEmailRequest()
                                .withDestinations(email.trim())
                                .withRawMessage(new RawMessage()
                                        .withData(ByteBuffer.wrap(Base64.getMimeDecoder().decode(encodedEmails))));

                        SendRawEmailResult result = awsConfig.credentials().sendRawEmail(request);
                        emailSuccessfullySent.add(email);
                        log.info("Mail sent to " + email);

                    }
                }
                catch (Exception e){
                    log.error("Exception while sending email ::: {} ::: {}", e, email);
                    blockedEmail.add("[ERR]"+email);
                    continue;
                }
            }
            try {
                SentMailStats sentMailStats = new SentMailStats();
                sentMailStats.setMailCampaignId(emailContent.getHeaderIdValue());
                sentMailStats.setSentEmail(emailSuccessfullySent.toString());
                sentMailStats.setBlockedEmail(blockedEmail.toString());
                sentMailStats.setSentEmailNumb(emailSuccessfullySent.size());
                sentMailStats.setBlockedEmailNumb(blockedEmail.size());
                sentMailStatsRepository.save(sentMailStats);

                if(customerDetails.containsKey("partnerId")){
                    Optional<PartnerMailLimit> partnerMailLimit = partnerMailLimitRepository.findByPartnerId(customerDetails.get("partnerId"));
                    if(partnerMailLimit.isPresent()){
                        PartnerMailLimit mailLimit = partnerMailLimit.get();
                        mailLimit.setConsumed(mailLimit.getConsumed()+emailSuccessfullySent.size());
                        partnerMailLimitRepository.save(mailLimit);
                    }
                }
            }
            catch (Exception ex){
                log.error("Exception while saving mail stats ::: {}", ex);
                return CompletableFuture.completedFuture("The email was not sent. Error message: " + ex.getMessage());
            }
            BulkMailAttempt bulkMailAttempt = bulkMailAttemptRepository.findByCampaignId(emailContent.getHeaderIdValue());
            bulkMailAttempt.setMailSent(true);
            bulkMailAttempt=bulkMailAttemptRepository.save(bulkMailAttempt);
            log.info("All mails sent successfully {}",bulkMailAttempt);
            return CompletableFuture.completedFuture("Email sent to " + to);
        } catch (Exception ex) {
            log.error(ex);
            return CompletableFuture.completedFuture("The email was not sent. Error message: " + ex.getMessage());
        }
    }
    private String replaceTemplateWithInlineCss(String process) {
        try {
            return
                    process
                            .replaceAll("<figure class=\"table\">","<figure style=\"display:table;margin: 0.9em auto;\">")
                            .replaceAll("<table>","<table style=\"border-collapse: collapse; border-spacing: 0; width: 100%; height: 100%; border: 1px double hsl(0, 0%, 70%);\">")
                            .replaceAll("<td>","<td style=\"min-width: 2em; text-align: center; padding: .4em; border: 1px solid hsl(0, 0%, 75%);\">")
                            .replaceAll("<th>","<td style=\"font-weight: bold; text-align: center; min-width: 2em; padding: .4em; border: 1px solid hsl(0, 0%, 75%);\">")
                            .replaceAll("<img", "<img style=\"display: block;margin: 0 auto;max-width: 100%;min-width: 100%;height: auto;line-height: 100%;text-decoration: none;border: 0;outline: none;\"")
                    ;
        }
        catch (Exception e){
            log.error("Exception while replacing template with inline css ::: {}", e);
        }
        return process;
    }
    private void setSocialLinks(MailContent emailContent, JSONObject socialLinks) {
        try {
            String facebookurl = socialLinks.get("facebookurl")+"";
            String linkedurl = socialLinks.get("linkedurl")+"";
            String twitterurl = socialLinks.get("twitterurl")+"";
            String instagramurl = socialLinks.get("instagramurl")+"";
            String socialLinkInTemplate = "<table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" class=\"social-table\" role=\"presentation\" style=\"mso-table-lspace: 0pt; mso-table-rspace: 0pt;\" width=\"156px\">\n" +
                    "                                            <tr>\n"
                    + (!StringUtils.isEmpty(facebookurl)?"<td style=\"padding:0 0px 0 0px; color: #0017ff;\"><a href=\""+facebookurl+"\" target=\"_blank\"><img alt=\"Facebook\" height=\"28\" src=\"https://wuelev8-common.s3.ap-south-1.amazonaws.com/common/brandingMaterial/facebook_mail.png\" style=\"display: block; height: auto; border: 0;\" title=\"facebook\" width=\"32\"/></a></td>\n":"")
                    + (!StringUtils.isEmpty(twitterurl)?"<td style=\"padding:0 0px 0 0px;\"><a href=\""+twitterurl+"\" target=\"_blank\"><img alt=\"Twitter\" height=\"28\" src=\"https://wuelev8-common.s3.ap-south-1.amazonaws.com/common/brandingMaterial/twitter_mail.png\" style=\"display: block; height: auto; border: 0;\" title=\"twitter\" width=\"32\"/></a></td>\n":"")
                    + (!StringUtils.isEmpty(instagramurl)?"<td style=\"padding:0 0px 0 0px;\"><a href=\""+instagramurl+"\" target=\"_blank\"><img alt=\"Instagram\" height=\"28\" src=\"https://wuelev8-common.s3.ap-south-1.amazonaws.com/common/brandingMaterial/instagram_m.png\" style=\"display: block; height: auto; border: 0;\" title=\"instagram\" width=\"32\"/></a></td>\n":"")
                    + (!StringUtils.isEmpty(linkedurl)?"<td style=\"padding:0 0px 0 0px;\"><a href=\""+linkedurl+"\" target=\"_blank\"><img alt=\"LinkedIn\" height=\"28\" src=\"https://wuelev8-common.s3.ap-south-1.amazonaws.com/common/brandingMaterial/linkedin_mail.png\" style=\"display: block; height: auto; border: 0;\" title=\"linkedin\" width=\"32\"/></a></td>\n":"")
                    + "</tr>\n</table>";
            emailContent.setSocialLinks(socialLinkInTemplate);
        }
        catch (Exception e){
            log.error("Exception while setting up social links");
            emailContent.setSocialLinks("");
        }
    }
    public boolean isS3Link(String link) {
        if (link == null || link.isEmpty()) {
            return false;
        }

        // Check if the URL starts with "s3://"
        if (link.startsWith("s3://")) {
            return true;
        }

        // Check if the URL contains ".s3.amazonaws.com" or ".s3"
        if (link.contains(".s3.amazonaws.com") || link.contains(".s3")) {
            return true;
        }

        // Check if the URL contains "s3-" followed by any characters before ".amazonaws.com"
        int indexS3 = link.indexOf("s3-");
        if (indexS3 != -1 && link.indexOf(".amazonaws.com", indexS3) != -1) {
            return true;
        }

        // Check if the URL contains ".s3-accelerate.amazonaws.com"
        if (link.contains(".s3-accelerate.amazonaws.com")) {
            return true;
        }

        // Check if the URL contains "s3." followed by any characters before ".amazonaws.com"
        int indexS3Dot = link.indexOf("s3.");
        if (indexS3Dot != -1 && link.indexOf(".amazonaws.com", indexS3Dot) != -1) {
            return true;
        }

        // If none of the above conditions are met, it's not an S3 link
        return false;
    }

    public Object sendMailWithUnsubscribeLink(MailContent emailContent, String to) {
        try {
            log.debug("Send mail config {} ::: {}", awsConfig, emailContent.getTemplateName());
            String encodedEmail = Base64.getEncoder().encodeToString(to.getBytes());
            String unsubscribeLink = (domainUrl+"/unsubscribe?id="+encodedEmail);
            emailContent.setUnsubscribeLink(unsubscribeLink);
            Context context = new Context();
            context.setVariable("content", emailContent);
            String process = templateEngine.process(emailContent.getTemplateName(), context);
            String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");

            // Sending email via SES
            SESMailMessage sesEmailMessage = new SESMailMessage();
            sesEmailMessage.setTo(to);
            sesEmailMessage.setBody(data);
            sesEmailMessage.setSubject(emailContent.getSubject());
            if (!StringUtils.isEmpty(emailContent.getBcc())) {
                sesEmailMessage.setBcc(emailContent.getBcc());
            }
            if (!StringUtils.isEmpty(emailContent.getCc())) {
                sesEmailMessage.setCc(emailContent.getCc());
            }
            awsConfig.build().sendEmail(sesEmailMessage);
            return "Sent";
        } catch (Exception e) {
            log.error("exception {}", e);
        }
        return "sent";
    }




}

