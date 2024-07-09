package com.mail.MailTool.custom.specification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchBulkMailsCriteria {
    private String campaignId;
    private String startDate;
    private String endDate;
    private String uId;
    private String platform;
    private boolean isScheduled;
    private boolean isMailSent;
    private boolean isCancelled;
}