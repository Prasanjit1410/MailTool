package com.mail.MailTool.dto.mail;

import com.mail.MailTool.model.MailContent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkMailRequestDto {

    private MailContent emailContent;
    private Date scheduledMailDateTime;
    private boolean scheduledMails;
    private String platform;
    private List<String> attachmentPaths;

}
