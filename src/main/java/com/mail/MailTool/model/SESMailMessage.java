package com.mail.MailTool.model;

import java.io.File;
import java.util.List;
import javax.mail.internet.MimeMessage;

public class SESMailMessage {

    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String body;
    private String from;  // Add the 'from' field
    private List<byte[]> rawFileList;
    private List<File> fileList;
    private MimeMessage mimeMessage;

    public SESMailMessage() {
    }

    // Getters and Setters
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<byte[]> getRawFileList() {
        return rawFileList;
    }

    public void setRawFileList(List<byte[]> rawFileList) {
        this.rawFileList = rawFileList;
    }

    public List<File> getFileList() {
        return fileList;
    }

    public void setFileList(List<File> fileList) {
        this.fileList = fileList;
    }

    public MimeMessage getMimeMessage() {
        return mimeMessage;
    }

    public void setMimeMessage(MimeMessage mimeMessage) {
        this.mimeMessage = mimeMessage;
    }

    public void send(MimeMessage mimeMessage) {
        this.mimeMessage = mimeMessage;
        // TODO Auto-generated method stub
    }
}
