package com.mail.MailTool.util.ses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SESMailMessage {

    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String body;
    private String unsubscribeLink;
    private List<byte[]> rawFileList;
    private List<File> fileList;
}
