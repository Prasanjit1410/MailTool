package com.mail.MailTool.service;

import com.mail.MailTool.dto.CombinedMailRequestDto;
import com.mail.MailTool.dto.CombinedMailRequestDto.EmailDetails;
import com.mail.MailTool.model.SESMailMessage;
import com.mail.MailTool.util.ses.SESClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.time.ZoneId;
import java.util.Date;

@Service
public class MailServiceImpl implements MailService {

    @Autowired
    private SESClientBuilder sesClientBuilder;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    public MailServiceImpl() {
        taskScheduler.initialize();
    }

    @Override
    public ResponseEntity<?> sendCombinedMail(CombinedMailRequestDto combinedMailRequestDto) {
        List<EmailDetails> emailDetailsList = combinedMailRequestDto.getEmailDetailsList();
        boolean isScheduled = combinedMailRequestDto.isScheduled();
        LocalDateTime scheduledDateTime = combinedMailRequestDto.getScheduledDateTime();
        List<String> attachmentPaths = combinedMailRequestDto.getAttachmentPaths();

        try {
            for (EmailDetails emailDetails : emailDetailsList) {
                SESMailMessage emailMessage = new SESMailMessage();
                emailMessage.setTo(emailDetails.getTo());
                emailMessage.setCc(emailDetails.getCc());
                emailMessage.setBcc(emailDetails.getBcc());
                emailMessage.setSubject(emailDetails.getSubject());
                emailMessage.setBody(emailDetails.getBody());
                emailMessage.setFrom(emailDetails.getFrom());

                if (isScheduled) {
                    long initialDelay = calculateInitialDelay(scheduledDateTime);
                    taskScheduler.schedule(() -> sendEmail(emailMessage, attachmentPaths), new Date(System.currentTimeMillis() + initialDelay));
                } else {
                    sendEmail(emailMessage, attachmentPaths);
                }
            }
            return ResponseEntity.ok("Combined email request processed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to process combined email request");
        }
    }

    private void sendEmail(SESMailMessage emailMessage, List<String> attachmentPaths) {
        sesClientBuilder.sendEmail(emailMessage); // Implement your email sending logic here
    }

    private long calculateInitialDelay(LocalDateTime scheduledDateTime) {
        return scheduledDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis();
    }

    @Override
    public ResponseEntity<?> cancelScheduledBulkMailRequest(String campaignId) {
        // Implementation for cancelling the scheduled mail
        return ResponseEntity.ok("Scheduled email cancelled successfully");
    }

    @Override
    public ResponseEntity<?> getBulkMailStatus(String campaignId) {
        // Implementation for getting the bulk mail status
        return ResponseEntity.ok("Bulk mail status retrieved successfully");
    }
}
