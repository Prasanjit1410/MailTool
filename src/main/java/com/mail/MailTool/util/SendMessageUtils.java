package com.mail.MailTool.util;



import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.mail.MailTool.domain.mail.*;
import com.mail.MailTool.repository.mail.*;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import com.amazonaws.services.simpleemail.model.RawMessage;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


import com.mail.MailTool.config.AWSConfig;
import com.mail.MailTool.dto.MailContent;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class SendMessageUtils {
    public final static Logger LOGGER = LoggerFactory.getLogger(SendMessageUtils.class);



    @Autowired
    private TemplateEngine templateEngine;



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
    BulkMailAttemptRepository bulkMailAttemptRepository;

    @Value("${FROM_EMAIL}")
    private String fromEmail;

    @Value("${DISPLAY_NAME}")
    private String displayName;

    @Value("${REPLYTO_EMAIL}")
    private String replyToEmail;

    @Value("${domain.url:}")
    private String domainUrl;

    @Value("${config_set:configset}")
    private String configSet;



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

            List<String> unsubscribedEmails = unsubscribedMailsRepository.findAll().stream()
                    .map(UnsubscribedMails::getEmailId)
                    .toList();

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

                JSONObject socialLinks = new JSONObject(customerDetails.get("socialLinks"));
                setSocialLinks(emailContent, socialLinks);

                log.info("from email ::: {} ::: {}", fromEmail);
            }

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

                    MimeBodyPart bodyPart = new MimeBodyPart();
                    bodyPart.setContent(process, "text/html; charset=UTF-8");

                    Properties props = System.getProperties();
                    Session session = Session.getDefaultInstance(props, null);
                    MimeMessage message = new MimeMessage(session);
                    message.addHeader(emailContent.getHeaderIdName(), emailContent.getHeaderIdValue());
                    message.setSubject(emailContent.getSubject());

                    MimeMultipart multipart = new MimeMultipart();
                    multipart.addBodyPart(bodyPart);

                    if (attachmentPaths != null && !attachmentPaths.isEmpty()) {
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

                                String key = getS3Key(parts);

                                log.info("key ::: {}", key);
                                S3Object s3Object = awsConfig.s3client().getObject(new GetObjectRequest(bucketName, key));
                                attachment = (DataSource) new ByteArrayDataSource(s3Object.getObjectContent(), s3Object.getObjectMetadata().getContentType());
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

                    if (emailContent.getCc() != null && !emailContent.getCc().isEmpty()) {
                        String[] ccEmails = emailContent.getCc().split(",");

                        InternetAddress[] ccAddresses = new InternetAddress[ccEmails.length];
                        for (int i = 0; i < ccEmails.length; i++) {
                            String ccEmail = ccEmails[i].trim();
                            if (isValidEmail(ccEmail)) {
                                try {
                                    ccAddresses[i] = new InternetAddress(ccEmail);
                                } catch (jakarta.mail.internet.AddressException e) {
                                    log.warn("Invalid CC email: {}", ccEmail);
                                }
                            } else {
                                log.warn("Invalid CC email format: {}", ccEmail);
                            }
                        }

                        message.setRecipients(MimeMessage.RecipientType.CC, ccAddresses);
                    }

                    if (emailContent.getBcc() != null && !emailContent.getBcc().isEmpty()) {
                        String[] bccEmails = emailContent.getBcc().split(",");

                        InternetAddress[] bccAddresses = new InternetAddress[bccEmails.length];
                        for (int i = 0; i < bccEmails.length; i++) {
                            String bccEmail = bccEmails[i].trim();
                            if (isValidEmail(bccEmail)) {
                                try {
                                    bccAddresses[i] = new InternetAddress(bccEmail);
                                } catch (jakarta.mail.internet.AddressException e) {
                                    log.warn("Invalid BCC email: {}", bccEmail);
                                }
                            } else {
                                log.warn("Invalid BCC email format: {}", bccEmail);
                            }
                        }

                        message.setRecipients(MimeMessage.RecipientType.BCC, bccAddresses);
                    }


                    message.setFrom(new InternetAddress(fromEmail, displayName));
                    message.setReplyTo(new jakarta.mail.Address[]{
                            new jakarta.mail.internet.InternetAddress(replyToEmail)
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
                } catch (Exception e){
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
            } catch (Exception ex){
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
    private String getS3Key(String[] parts ){
        int index=0;
        for(int i=0;i<parts.length;i++){
            if(parts[i].endsWith("amazonaws.com")){
                index=i;
            }
        }
        if(index== parts.length-2){
            return parts[parts.length-1];
        }
        String key="";
        for(int i=index+1;i<parts.length;i++){

            if(i!=parts.length-1){
                key+= parts[i]+"/";
            }
            else{
                key+=parts[i];
            }
        }
        return key;
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






}