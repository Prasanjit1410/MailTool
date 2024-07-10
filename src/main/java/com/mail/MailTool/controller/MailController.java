package com.mail.MailTool.controller;

import com.mail.MailTool.constant.SearchProfile;
import com.mail.MailTool.domain.mail.MailStatus;
import com.mail.MailTool.domain.mail.UnsubscribedMails;
import com.mail.MailTool.dto.mail.BulkMailRequestDto;
import com.mail.MailTool.service.MailService;
import com.mail.MailTool.util.CommonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@RestController
@RequestMapping("/")
public class MailController {

    @Autowired
    private MailService mailService;
    @Autowired
    CommonUtils commonUtils;
    @PostMapping("/api/mails/bulk")
    public ResponseEntity<?> sendMailToBulkUsersV1(
            @RequestBody BulkMailRequestDto payload,
            @RequestParam(defaultValue = "NA", required = false) String uId,
            HttpServletRequest request) {
        return mailService.sendBulkMailV1(payload, uId, commonUtils.setUser(request));
    }

    @PostMapping("/api/v1/mails/status")
    public ResponseEntity<?> addStatusOfAMailToAReceiver(
            @RequestBody MailStatus mailStatus)
    {
        return mailService.saveMailStatus(mailStatus);
    }

    @PostMapping(value = "api/v1/mails/readFromFile", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> register(
            @RequestParam(name = "file") MultipartFile file) {
        return mailService.readFile(file, "emails");
    }

    @PostMapping("/api/v1/mails/saveunsubscribemail")
    public ResponseEntity<?> addUnsubscribedMailController(@RequestBody UnsubscribedMails unsubscribedMail) {
        return mailService.addUnsubscribedMail(unsubscribedMail);
    }


    @PostMapping("/api/v1/mails/uploadfile")
    public ResponseEntity<?> uploadFileToS3(@RequestPart(value = "file") MultipartFile file) {
        return  mailService.uploadFileToS3(file);
    }

    @DeleteMapping("/api/v1/mails/bulk/cancel/scheduled/{campaignId}")
    public ResponseEntity<?> cancelScheduledBulkMailRequest(@PathVariable String campaignId) {
        return mailService.cancelScheduledBulkMailRequest(campaignId);
    }

    @GetMapping("/api/v1/mails/search")
    public ResponseEntity<?> searchBulkMails(
            @RequestParam(defaultValue = "NA", required = false) String campaignId,
            @RequestParam(defaultValue = "NA", required = false) String startDate,
            @RequestParam(defaultValue = "NA", required = false) String endDate,
            @RequestParam(defaultValue = "NA", required = false) String uId,
            @RequestParam(defaultValue = "NA", required = false) String platform,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "sentDt.desc") String order,
            @RequestParam(defaultValue = "BASIC", required = false) SearchProfile profile,
            @RequestParam(defaultValue = "false", required = false) boolean isScheduled,
            @RequestParam(defaultValue = "false", required = false) boolean isMailSent,
            @RequestParam(defaultValue = "false", required = false) boolean isCancelled,
            HttpServletRequest request) {
        return mailService.searchBulkMails(campaignId, startDate, endDate, uId, platform,
                offset, limit, order, profile, commonUtils.setUser(request), isMailSent, isScheduled, isCancelled);
    }


}