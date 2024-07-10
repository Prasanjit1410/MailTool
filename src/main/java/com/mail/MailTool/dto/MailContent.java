package com.mail.MailTool.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class MailContent {
    private String subject;
    private String heading;
    private String message;
    private String link;
    private String templateName;
    private String otp;
    private String unsubscribeLink;
    private String userName;
    private String mobile;
    private String to;
    private String cc;
    private String bcc;
    private String headerIdName;
    private String headerIdValue;
    private String socialLinks;
    private Map<String, String> customerDetails;


}