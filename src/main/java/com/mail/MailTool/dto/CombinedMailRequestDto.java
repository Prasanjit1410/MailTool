package com.mail.MailTool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombinedMailRequestDto {

    private EmailContent emailContent;
    private LocalDateTime scheduledMailDateTime;
    private boolean isScheduledMails;
    private String platform;
    private List<String> attachmentPaths;

}
