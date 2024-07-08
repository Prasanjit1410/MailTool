package com.mail.MailTool.controller;

import com.mail.MailTool.dto.CombinedMailRequestDto;
import com.mail.MailTool.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class MailController {

    @Autowired
    private MailService mailService;

    @PostMapping("/send")
    public ResponseEntity<?> sendCombinedMail(@RequestBody CombinedMailRequestDto combinedMailRequestDto) {
        return mailService.sendCombinedMail(combinedMailRequestDto);
    }

    @DeleteMapping("/bulk/cancel/scheduled/{campaignId}")
    public ResponseEntity<?> cancelScheduledBulkMailRequest(@PathVariable String campaignId) {
        return mailService.cancelScheduledBulkMailRequest(campaignId);
    }

    @GetMapping("api/v1/bulk/status/{campaignId}")
    public ResponseEntity<?> getBulkMailStatus(@PathVariable String campaignId) {
        return mailService.getBulkMailStatus(campaignId);
    }
}
