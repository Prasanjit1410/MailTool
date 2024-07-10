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
    @PostMapping("/api/v1/mails/bulk")
    public ResponseEntity<?> sendMailToBulkUsersV1(
            @RequestBody BulkMailRequestDto payload,
            @RequestParam(defaultValue = "NA", required = false) String uId,
            HttpServletRequest request) {
        return mailService.sendBulkMailV1(payload, uId, commonUtils.setUser(request));
    }

    @PostMapping("/api/v1/mails/status")
    public ResponseEntity<?> addStatusOfAMailToAReceiver(
            @RequestBody MailStatus mailStatus,
            HttpServletRequest request) {
        return new ResponseEntity<>(mailService.saveMailStatus(mailStatus), HttpStatus.OK);
    }
    @PostMapping(value = "api/v1/mails/readFromFile", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> register(
            @RequestParam(name = "file") MultipartFile file,
            javax.servlet.http.HttpServletRequest request) {
        try {
            return new ResponseEntity<Map<String, ?>>(mailService.readFile(file, "emails"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"Error\":\"Please fill in correct details.\"}",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping("/api/v1/mails/saveunsubscribemail")
    public ResponseEntity<Object> addUnsubscribedMail(@RequestBody UnsubscribedMails unsubscribedMail) {
        try {
            Object addedMail = mailService.addUnsubscribedMail(unsubscribedMail);
            return new ResponseEntity<>(addedMail, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(commonUtils.message(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Exception while adding unsubscribed mail to support@wuelev8.tech"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @DeleteMapping("/api/v1/mails/bulk/cancel/scheduled/{campaignId}")
    public ResponseEntity<?> cancelScheduledBulkMailRequest(@PathVariable String campaignId) {
        return mailService.cancelScheduledBulkMailRequest(campaignId);
    }

    @GetMapping("/api/v1/mails/bulk/search")
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
