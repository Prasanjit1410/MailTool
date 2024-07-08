package com.mail.MailTool.service;

import com.mail.MailTool.dto.CombinedMailRequestDto;
import org.springframework.http.ResponseEntity;

public interface MailService {
    ResponseEntity<?> sendCombinedMail(CombinedMailRequestDto combinedMailRequestDto);
    ResponseEntity<?> cancelScheduledBulkMailRequest(String campaignId);
    ResponseEntity<?> getBulkMailStatus(String campaignId);
}
