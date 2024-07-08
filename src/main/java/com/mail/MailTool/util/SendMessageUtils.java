package com.mail.MailTool.util;



import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.mail.MailTool.config.AWSConfig;
//import com.bmj.exception.ApiException;
//import com.bmj.model.EmailContent;
//import com.bmj.model.Response;
import com.mail.MailTool.dto.Response;
import com.mail.MailTool.exception.ApiException;
import com.mail.MailTool.model.MailContent;
import com.mail.MailTool.model.SESMailMessage;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.validator.routines.EmailValidator;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.Properties;

@Component
@Log4j2
public class SendMessageUtils {


    public final static Logger LOGGER = LoggerFactory.getLogger(SendMessageUtils.class);

    @Value("${MSG_91_AUTH_KEY}")
    private String MSG_91_AUTH_KEY;

    @Value("${MSG_91_SENDER_ID}")
    private String MSG_91_SENDER_ID;
    @Value("${MSG_91_OTP_TEMPLATE}")
    private String MSG_91_OTP_TEMPLATE;

    @Value("${MSG_91_MESSAGE_TEMPLATE}")
    private String MSG_91_MESSAGE_TEMPLATE;

    @Autowired
    private final TemplateEngine templateEngine;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private AWSConfig awsConfig;
    @Autowired
    public AmazonSimpleEmailService getSESClient;

    public ResponseEntity<String> sendMessage(String mobileNumber, String otp) throws ApiException {
        try {
           /* LOGGER.info("sending text message");
            String apiKey = "apikey=" + "/4mJHP2NbEs-y9wd7KFNwYgkkkm4X6ON0OzXNMvmGe";
            String message = "&message=" + "OTP for A2ZWHS : " + otp;
            String sender = "&sender=" + "TXTLCL";
            String numbers = "&numbers=" + 91 + mobileNumber;
            HttpURLConnection conn = (HttpURLConnection) new URL("https://api.textlocal.in/send/?")
                    .openConnection();
            String data = apiKey + numbers + message + sender;
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Length", Integer.toString(data.length()));
            conn.getOutputStream().write(data.getBytes("UTF-8"));

            final BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            LOGGER.info(rd.toString());
            final StringBuffer stringBuffer = new StringBuffer();

            String line;
            while ((line = rd.readLine()) != null) {
                stringBuffer.append(line);
                LOGGER.info(stringBuffer.toString());
            }
            rd.close();*/
            LOGGER.info("Message sent successfully");
            return new ResponseEntity<String>("Message sent successfully", HttpStatus.OK);
        } catch (Exception exception) {
            LOGGER.error(exception.getMessage());
            throw new ApiException(500, "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Response sendMessage(String mobileNumber, String number, Boolean isOtp) throws ApiException {
        String authKey = MSG_91_AUTH_KEY ;
        String senderId = MSG_91_SENDER_ID;
        String otpTemplate = MSG_91_OTP_TEMPLATE;
        int otp_length = 6;
        //String msg = String.format(CommonConstants.MSG_WOW_OTP_TEMPLATE, number);
        //encoding message
        String encoded_message = URLEncoder.encode(number);
        //Prepare Url
        URLConnection myURLConnection = null;
        URL myURL = null;
        BufferedReader reader = null;
        String mainUrl = null;
        //Send SMS API
        if (isOtp)
            mainUrl = "https://api.msg91.com/api/v5/otp?";
        else
            mainUrl = "https://api.msg91.com/api/sendhttp.php?";

        //Prepare parameter string
        StringBuilder sbPostData = new StringBuilder(mainUrl);
        sbPostData.append("authkey=" + authKey);
        sbPostData.append("&mobiles=" + mobileNumber);
        if (mobileNumber.startsWith("+")){
            sbPostData.append("&country=");
        }else {
            sbPostData.append("&country=91");
        }


        if (isOtp) {
            //sbPostData.append("&otp=" + encoded_message);
            sbPostData.append("&otp=" + encoded_message);
            sbPostData.append("&template_id=" + otpTemplate);
            sbPostData.append("&otp_length=" +otp_length);
            sbPostData.append("&invisible=" + 1);
        } else {
            sbPostData.append("&otp=" + encoded_message);
            sbPostData.append("&route=4");
        }
        sbPostData.append("&sender=" + senderId);

        //final string
        mainUrl = sbPostData.toString();
        try {
            //prepare connection
            myURL = new URL(mainUrl);
            myURLConnection = myURL.openConnection();
            myURLConnection.connect();
            reader = new BufferedReader(new InputStreamReader(myURLConnection.getInputStream()));
            //reading response
            String response;
            while ((response = reader.readLine()) != null)
                //print response
                LOGGER.info("Send sms api response is : " + response);

            //finally close connection
            reader.close();
            LOGGER.info("Message Sent successfully");
            return new Response(200,"Message sent successfully", HttpStatus.OK);
        } catch (IOException exception) {
            LOGGER.info("Message not Send");
            return new Response(500,exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @Async
    public String sendMail(String to, MailContent emailContent) throws MessagingException {
        try {
            EmailValidator emailValidator = EmailValidator.getInstance();
            if(!emailValidator.isValid(to)){
                log.info("Email is invalid {}", to);
                return "Email is Invalid";
            }
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

    public String sendMailForGenericUsers(String to, MailContent emailContent) throws MessagingException {
        try {
            EmailValidator emailValidator = EmailValidator.getInstance();
            if(!emailValidator.isValid(to)){
                log.info("Email is invalid \"{}\" ", to);
                return "\""+to+"\" email is invalid";
            }
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




//    @Async
//    public String sendMailWithAttachment(String to, EmailContent emailContent, File FileToAttach) throws MessagingException {
//        try {
//
//            log.debug("Send mail config {} ::: {}", awsConfig, emailContent.getTemplateName());
//            Context context = new Context();
//            context.setVariable("content", emailContent);
//            String process = templateEngine.process(emailContent.getTemplateName(), context);
//            String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");
//
//            //SENDING EMAIL VIA SES
//            SESEmailMessage sesEmailMessage = new SESEmailMessage();
//            sesEmailMessage.setTo(to);
//            sesEmailMessage.setBody(data);
//            sesEmailMessage.setSubject(emailContent.getSubject());
//            if(!StringUtils.isEmpty(emailContent.getBcc())){
//                sesEmailMessage.setBcc(emailContent.getBcc());
//            }
//            if(!StringUtils.isEmpty(emailContent.getCc())){
//                sesEmailMessage.setCc(emailContent.getCc());
//            }
//            BodyPart messageBodyPart = new MimeBodyPart();
//            messageBodyPart.setText("Mail Body");
//            MimeBodyPart attachmentPart = new MimeBodyPart();
//            attachmentPart.attachFile( FileToAttach);
//            awsConfig.build().sendEmail(sesEmailMessage);
//            return "Sent";
//        }
//        catch (Exception e) {
//            log.error("exception {}", e);
//        }
//        return "sent";
//    }

    public SendMessageUtils(TemplateEngine templateEngine, JavaMailSender javaMailSender) {
        this.templateEngine = templateEngine;
        this.javaMailSender = javaMailSender;
    }

    /**
     * This is the sendEmail method it
     * Receive email-id as a parameter and send email to that email-id.
     *
     * @return Response object as a response in json format
     */
    @Async
    public ResponseEntity<String> sendEmail(SimpleMailMessage email) {
        javaMailSender.send(email);
        return new ResponseEntity<String>("Message sent successfully", HttpStatus.OK);
    }

    public void sendEmailWithAttachment(String to, MailContent emailContent, byte[] attachmentData, String attachmentName) throws MessagingException, IOException {
        // Create a Properties object to contain connection configuration information.
        Properties props = System.getProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.port", 25);

        // Create a Session object to represent a mail session with the specified properties.
        Session session = Session.getDefaultInstance(props);
        Context context = new Context();
        context.setVariable("content", emailContent);
        String process = templateEngine.process(emailContent.getTemplateName(), context);
        String data = process.replaceAll("&lt;", "<").replaceAll("&amp;", "&").replaceAll("&gt;", ">");

        // Create a new MimeMessage object.
        MimeMessage message = new MimeMessage(session);

        // Add subject, from, and to lines.
        message.setSubject(emailContent.getSubject(), "UTF-8");
        message.setFrom(new InternetAddress(awsConfig.fromEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

        // Create a multipart/alternative child container.
        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(data, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        // Create a wrapper for the HTML and text parts.
        MimeBodyPart wrap = new MimeBodyPart();
        wrap.setContent(multipart);

        // Create a multipart/mixed parent container.
        Multipart msg = new MimeMultipart("mixed");
        message.setContent(msg);

        // Add the parent container to the message.
        msg.addBodyPart(wrap);

        // Define the attachment
        MimeBodyPart att = new MimeBodyPart();
        att.setDataHandler(new DataHandler(new ByteArrayDataSource(attachmentData, "application/pdf")));
        att.setFileName(attachmentName);

        // Add the attachment to the message.
        msg.addBodyPart(att);

        // Send the email.
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            message.writeTo(outputStream);
            ByteBuffer buf = ByteBuffer.wrap(outputStream.toByteArray());

            RawMessage rawMessage = new RawMessage(buf);

            SendRawEmailRequest rawEmailRequest = new SendRawEmailRequest(rawMessage);

            getSESClient.sendRawEmail(rawEmailRequest);
        }
    }

    public Response sendMessages(String mobileNumber, String contact, String link) {
        log.info("messages :::{} :::{} :::{}",mobileNumber,contact,link);
        String AUTH_KEY = MSG_91_AUTH_KEY;
        String templateId = MSG_91_MESSAGE_TEMPLATE;
        boolean shortUrl = true;
        String MSG91_API_URL = "https://control.msg91.com/api/v5/flow";
        try {
            String body = String.format("{\n" +
                    "  \"template_id\": \"%s\",\n" +
                    "  \"short_url\": \"%d\",\n" +
                    "  \"recipients\": [\n" +
                    "    {\n" +
                    "      \"mobiles\": \"%s\",\n" +
                    "      \"CONTACT\": \"%s\",\n" +
                    "      \"LINK\": \"%s\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}", templateId, shortUrl ? 1 : 0, mobileNumber, contact, link);
            log.info("Request body: {}", body);

            HttpResponse<String> response = Unirest.post(MSG91_API_URL)
                    .header("authkey", AUTH_KEY)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .body(body)
                    .asString();
            log.info("Response: {}", response.getBody());

            if (response.getStatus() == 200) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                String message = jsonResponse.getString("message");
                String type = jsonResponse.getString("type");
                log.info("Message sent successfully: Type: {}", type);
                return new Response(200, "Message sent successfully", type);
            } else {
                log.error("Failed to send message. HTTP status: {}", response.getStatus());
                return new Response(400, "Message not sent successfully", null);
            }
        } catch (Exception e) {
            LOGGER.info("Message not Send");
            return new Response(500, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }

}



