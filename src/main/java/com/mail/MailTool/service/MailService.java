package com.mail.MailTool.service;

import com.mail.MailTool.domain.mail.*;
import com.mail.MailTool.dto.mail.BulkMailRequestDto;
import com.mail.MailTool.model.MailContent;
import com.mail.MailTool.repository.mail.*;
import com.mail.MailTool.util.CommonUtils;
import com.mail.MailTool.util.InternityUser;
import com.mail.MailTool.util.SendMessageUtils;
import com.mail.MailTool.util.drill.ResponseUtil;
import com.mail.MailTool.util.drill.SendCustomMailUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

@Service
@Log4j2
public class MailService {
    @Value("${}")
    String mainFileName;

    @Autowired
    SendCustomMailUtils sendCustomMailUtils;

    @Autowired
    SendMessageUtils messageUtils;

    @Autowired
    MailStatusRepository mailStatusRepository;

    @Autowired
    UnsubscribedMailRepository unsubscribedMailRepository;

    @Autowired
    BulkMailAttemptRepository bulkMailAttemptRepository;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    SentMailStatsRepository sentMailStatsRepository;

    @Autowired
    PartnerMailLimitRepository partnerMailLimitRepository;

    @Autowired
    ResponseUtil responseUtil;

    EmailValidator emailValidator = EmailValidator.getInstance();

    private EntityManager entityManager;
    public MailService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    public ResponseEntity<?> sendBulkMailV1(BulkMailRequestDto payload, String uId, InternityUser internityUser) {
        try {
            log.info("Sending bulk mail with  uId: {}, attachmentPaths: {}, isScheduledMails: {}, scheduledMailDateTime: {}", uId, payload.getAttachmentPaths(), payload.isScheduledMails(), payload.getScheduledMailDateTime());
            MailContent emailContent = payload.getEmailContent();

            log.info("emailContent: {}", emailContent);
            log.info("payload {}", payload);

            BulkMailAttempt bulkMailAttempt = new BulkMailAttempt();
            bulkMailAttempt.setCampaignId("MID-" + Instant.now().toEpochMilli());
            bulkMailAttempt.setMailContent(emailContent.getMessage());
            bulkMailAttempt.setRecipientListTo(emailContent.getTo());
            bulkMailAttempt.setRecipientListCc(emailContent.getCc());
            bulkMailAttempt.setRecipientListBcc(emailContent.getBcc());
            bulkMailAttempt.setSubject(emailContent.getSubject());
            if (payload.isScheduledMails()) {
                bulkMailAttempt.setScheduledMail(true);
                bulkMailAttempt.setSentDt(payload.getScheduledMailDateTime());
            } else {
                bulkMailAttempt.setScheduledMail(false);
                bulkMailAttempt.setSentDt(LocalDateTime.now());
            }
            bulkMailAttempt.setUId(uId);
            bulkMailAttempt.setPlatform(payload.getPlatform().toUpperCase());

            if (emailContent.getCustomerDetails() != null) {
                Map<String, String> customerDetails = emailContent.getCustomerDetails();
                bulkMailAttempt.setPartnerId(customerDetails.getOrDefault("partnerId", "NA"));
            }
            bulkMailAttempt = bulkMailAttemptRepository.save(bulkMailAttempt);
            String campaignId = bulkMailAttempt.getCampaignId();
            log.info("campaignId: {}", campaignId);
            emailContent.setHeaderIdName("BULK-CAMPAIGN-ID");
            emailContent.setHeaderIdValue(campaignId);
            log.info("emailContent after changes: {}", emailContent);

            if (payload.isScheduledMails()) {
                log.info("This is a scheduled bulk mail request");
                long initialDelay = calculateInitialDelay(payload.getScheduledMailDateTime());
                ScheduledFuture<?> future = scheduler.schedule(() -> sendBulkMail(emailContent, payload.getAttachmentPaths()), initialDelay, TimeUnit.MILLISECONDS);
                scheduledTasks.put(campaignId, future);

                log.info("Bulk mail scheduled successfully");
                return new ResponseEntity<>(responseUtil.successResponse("Bulk mail scheduled successfully", bulkMailAttempt), HttpStatus.OK);
            }
            log.info("This is a non-scheduled bulk mail request");
            sendBulkMail(emailContent, payload.getAttachmentPaths());

            log.info("All mail sent successfully");
            return new ResponseEntity<>(responseUtil.successResponse("All mail sent successfully", bulkMailAttempt), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Exception while sending bulk mail: {}", e.getMessage());
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while sending bulk mail"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    private long calculateInitialDelay(LocalDateTime scheduledDateTime) {
        log.info("Scheduled time: {}", scheduledDateTime);
        LocalDateTime currentDateTime = LocalDateTime.now();
        return Math.max(0, java.time.Duration.between(currentDateTime, scheduledDateTime).toMillis());
    }
    private void sendBulkMail(MailContent emailContent, List<String> attachmentPaths) {
        log.info("Sending bulk mail");
        messageUtils.sendMailWithCustomHeaderAndAttachment(emailContent, emailContent.getTo(), attachmentPaths);
    }
    public ResponseEntity<?> cancelScheduledBulkMailRequest(String campaignId) {
        try {
            log.info("Cancelling scheduled bulk mail request with campaignId: {}", campaignId);
            ScheduledFuture<?> future = scheduledTasks.remove(campaignId);
            if (future != null) {
                future.cancel(true);
                BulkMailAttempt attempt=bulkMailAttemptRepository.findByCampaignId(campaignId);
                attempt.setScheduledMailCancelled(true);
                bulkMailAttemptRepository.save(attempt);
                log.info("Cancelling scheduled bulk mail request with campaignId: {} successfully", campaignId);
                return new ResponseEntity<>(responseUtil.successResponse("Scheduled email canceled for campaign ID: " + campaignId, null), HttpStatus.OK);
            } else {
                log.info("No scheduled email found for campaign ID: {}", campaignId);
                return new ResponseEntity<>(responseUtil.notFoundResponse("No scheduled email found for campaign ID: " + campaignId), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            log.error("Exception while cancelling scheduled bulk mail request: {}", e.getMessage());
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while cancelling scheduled bulk mail request"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public Map<String, Object> filterCampaigns(String campaignId, String partnerId, String startDate, String endDate) {
        try {
            Map<String, Object> response = new HashMap<>();
            List<BulkMailAttempt> campaigns;

            if (!("NA".equals(partnerId)) && "NA".equals(campaignId)) {
                log.info("returning all only for partner");
                campaigns = bulkMailAttemptRepository.findByPartnerId(partnerId);
            } else {
                if (campaignId.equals("NA") && startDate.equals("NA") && endDate.equals("NA")) {
                    log.info("Filtering campaigns - No filtering parameters provided. Returning all campaigns.");
                    campaigns = bulkMailAttemptRepository.findAll();
                } else if (!campaignId.equals("NA") && startDate.equals("NA") && endDate.equals("NA")) {
                    log.info("Filtering campaigns - Campaign ID: {}", campaignId);
                    campaigns = bulkMailAttemptRepository.findAllByCampaignId(campaignId);
                } else if (!startDate.equals("NA") && !endDate.equals("NA")) {
                    log.info("Filtering campaigns - Start Date: {}, End Date: {}", startDate, endDate);
                    campaigns = filterByDate(startDate, endDate);
                } else {
                    log.error("Invalid parameters provided.");
                    throw new IllegalArgumentException("Invalid parameters provided.");
                }
            }
            List<BulkMailAttempt> campaignListParent = campaigns.stream().distinct().collect(Collectors.toList());
            List<BulkMailAttempt> campaignList = setCountOfSuccessfulMailsSent(campaignListParent);
            List<MailStatus> allCampaign = mailStatusRepository.findAll();

            response.put("totalRecords", campaignList.size());
            response.put("data", campaignList);
            response.put("AllList", allCampaign);
            return response;
        } catch (Exception e) {
            log.error("Exception while filtering campaigns: {}", e);
            return null;
        }
    }
    private List<BulkMailAttempt> filterByDate(String startDate, String endDate) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<BulkMailAttempt> query = criteriaBuilder.createQuery(BulkMailAttempt.class);
        Root<BulkMailAttempt> root = query.from(BulkMailAttempt.class);

        Predicate startDatePredicate = criteriaBuilder.greaterThanOrEqualTo(root.get("sentDt"), startDate);
        Predicate endDatePredicate = criteriaBuilder.lessThanOrEqualTo(root.get("sentDt"), endDate);
        Predicate finalPredicate = criteriaBuilder.and(startDatePredicate, endDatePredicate);

        query.where(finalPredicate);

        TypedQuery<BulkMailAttempt> typedQuery = entityManager.createQuery(query);
        List<BulkMailAttempt> filteredAttempts = typedQuery.getResultList();
        log.info("Filtered {} campaigns by date.", filteredAttempts.size());
        return filteredAttempts;
    }
    private List<BulkMailAttempt> setCountOfSuccessfulMailsSent(List<BulkMailAttempt> campaignListParent) {
        try {
            if (campaignListParent == null || campaignListParent.isEmpty()) {
                return campaignListParent;  // No processing needed for empty list
            }

            List<String> campaignWithSuccessfulMailSent = new ArrayList<>();
            for (BulkMailAttempt mailAttempt : campaignListParent) {
                if (StringUtils.isNoneBlank(mailAttempt.getCampaignId())) {
                    campaignWithSuccessfulMailSent.add(mailAttempt.getCampaignId());
                }
            }

            List<SentMailStats> sentMailStatsList = sentMailStatsRepository.findByMailCampaignIdIn(campaignWithSuccessfulMailSent);
            Map<String, SentMailStats> sentMailStatsMap = sentMailStatsList.stream()
                    .filter(stats -> StringUtils.isNoneBlank(stats.getMailCampaignId()))
                    .collect(Collectors.toMap(SentMailStats::getMailCampaignId, stats -> stats));

            List<BulkMailAttempt> result = new ArrayList<>();
            for (BulkMailAttempt mailAttempt : campaignListParent) {
                if (StringUtils.isNoneBlank(mailAttempt.getCampaignId())) {
                    mailAttempt.setSentMailStatsDetails(sentMailStatsMap.get(mailAttempt.getCampaignId()));
                }
                result.add(mailAttempt);
            }
            return result;
        } catch (Exception e) {
            log.error("Exception while setting count of successful mails sent ::: {}", e.getMessage());
            return campaignListParent;  // Consider what to return in case of exception
        }
    }




}
