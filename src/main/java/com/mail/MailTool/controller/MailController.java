package com.mail.MailTool.controller;

import com.mail.MailTool.dto.mail.BulkMailRequestDto;
import com.mail.MailTool.service.MailService;
import com.mail.MailTool.util.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

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

    @DeleteMapping("/api/v1/mails/bulk/cancel/scheduled/{campaignId}")
    public ResponseEntity<?> cancelScheduledBulkMailRequest(@PathVariable String campaignId) {
        return mailService.cancelScheduledBulkMailRequest(campaignId);
    }

    @GetMapping("api/v1/mails/campaigns")
    public ResponseEntity<?> getCampaigns(
            @RequestParam(name = "campaignId", defaultValue = "NA") String campaignId,
            @RequestParam(name = "partnerId", defaultValue = "NA", required = true) String partnerId,
            @RequestParam(name = "startDate", defaultValue = "NA") String startDate,
            @RequestParam(name = "endDate", defaultValue = "NA") String endDate) {
        return new ResponseEntity<>(mailService.filterCampaigns(campaignId, partnerId, startDate, endDate), HttpStatus.OK);

    }

}
