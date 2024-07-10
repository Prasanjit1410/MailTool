package com.mail.MailTool.service;

import com.mail.MailTool.custom.specification.SearchBulkMailsCriteria;
import com.mail.MailTool.domain.mail.*;
import com.mail.MailTool.dto.mail.BulkMailRequestDto;
import com.mail.MailTool.dto.search.BulkMailAttemptSpecification;
import com.mail.MailTool.model.MailContent;
import com.mail.MailTool.repository.mail.*;
import com.mail.MailTool.util.CommonUtils;
import com.mail.MailTool.util.InternityUser;
import com.mail.MailTool.util.SendMessageUtils;
import com.mail.MailTool.util.drill.ResponseUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.time.Instant;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.mail.MailTool.constant.SearchProfile;
import org.springframework.web.multipart.MultipartFile;

@Service
@Log4j2
public class MailService {





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
            log.info("Sending bulk mail with uId: {}, attachmentPaths: {}, isScheduledMails: {}, scheduledMailDateTime: {}", uId, payload.getAttachmentPaths(), payload.isScheduledMails(), payload.getScheduledMailDateTime());
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
                bulkMailAttempt.setSentDt(new Date());
            }
            bulkMailAttempt.setUId(uId);
            bulkMailAttempt.setPlatform(payload.getPlatform().toUpperCase());


            bulkMailAttempt = bulkMailAttemptRepository.save(bulkMailAttempt);
            String campaignId = bulkMailAttempt.getCampaignId();
            log.info("campaignId: {}", campaignId);
            emailContent.setHeaderIdName("BULK-CAMPAIGN-ID");
            emailContent.setHeaderIdValue(campaignId);
            log.info("emailContent after changes: {}", emailContent);
            log.info("isScheduledMails: {}",payload.isScheduledMails());
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


    private long calculateInitialDelay(Date scheduledDate) {
        log.info("Scheduled time: {}", scheduledDate);
        Date currentDate = new Date();
        long delay = scheduledDate.getTime() - currentDate.getTime();
        return Math.max(0, delay);
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
                BulkMailAttempt attempt = bulkMailAttemptRepository.findByCampaignId(campaignId);
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

//    public Map<String, Object> filterCampaigns(String campaignId, String startDate, String endDate) {
//        try {
//            Map<String, Object> response = new HashMap<>();
//            List<BulkMailAttempt> campaigns;
//
//            if (campaignId.equals("NA") && startDate.equals("NA") && endDate.equals("NA")) {
//                log.info("Filtering campaigns - No filtering parameters provided. Returning all campaigns.");
//                campaigns = bulkMailAttemptRepository.findAll();
//            } else if (!campaignId.equals("NA") && startDate.equals("NA") && endDate.equals("NA")) {
//                log.info("Filtering campaigns - Campaign ID: {}", campaignId);
//                campaigns = bulkMailAttemptRepository.findAllByCampaignId(campaignId);
//            } else if (!startDate.equals("NA") && !endDate.equals("NA")) {
//                log.info("Filtering campaigns - Start Date: {}, End Date: {}", startDate, endDate);
//                campaigns = filterByDate(startDate, endDate);
//            } else {
//                log.error("Invalid parameters provided.");
//                throw new IllegalArgumentException("Invalid parameters provided.");
//            }
//
//            List<BulkMailAttempt> campaignListParent = campaigns.stream().distinct().collect(Collectors.toList());
//            List<BulkMailAttempt> campaignList = setCountOfSuccessfulMailsSent(campaignListParent);
//            List<MailStatus> allCampaign = mailStatusRepository.findAll();
//
//            response.put("totalRecords", campaignList.size());
//            response.put("data", campaignList);
//            response.put("AllList", allCampaign);
//            return response;
//        } catch (Exception e) {
//            log.error("Exception while filtering campaigns: {}", e);
//            return null;
//        }
//    }
    public ResponseEntity<?> searchBulkMails(String campaignId, String startDate, String endDate, String uId, String platform, int offset, int limit, String order, SearchProfile profile, InternityUser internityUser, boolean isMailSent, boolean isScheduled, boolean isCancelled) {
        try {
            log.info("Searching bulk mails with campaignId: {}, startDate: {}, endDate: {}, uId: {}, platform: {}, offset: {}, limit: {}, order: {}, profile: {}",
                    campaignId, startDate, endDate, uId, platform, offset, limit, order, profile);

            SearchBulkMailsCriteria searchCriteria = new SearchBulkMailsCriteria(campaignId, startDate, endDate, uId, platform, isScheduled, isMailSent, isCancelled);

            List<BulkMailAttempt> entries;
            String[] sort = order.split("\\.");
            Sort direction = Sort.by("desc".equalsIgnoreCase(sort[1]) ? Sort.Direction.DESC : Sort.Direction.ASC, sort[0]);
            Pageable pageable = PageRequest.of(offset, limit, direction);

            log.info("Start retrieving bulk mail entries from the database");
            Page<BulkMailAttempt> page = bulkMailAttemptRepository.findAll(new BulkMailAttemptSpecification(searchCriteria, bulkMailAttemptRepository), pageable);
            log.info("Retrieved bulk mail entries from the database successfully");

            entries = setCountOfSuccessfulMailsSent(page.getContent().stream().distinct().collect(Collectors.toList()));

            log.info("Bulk mails retrieved successfully");
            return new ResponseEntity<>(responseUtil.successResponse("Bulk mails retrieved successfully", entries), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Exception while searching bulk mails: {}", e.getMessage());
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while searching bulk mails"), HttpStatus.INTERNAL_SERVER_ERROR);
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
                return campaignListParent; // No processing needed for empty list
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
            return campaignListParent; // Consider what to return in case of exception
        }
    }
    public MailStatus saveMailStatus(MailStatus mailStatus) {
        try {
            return mailStatusRepository.save(mailStatus);
        } catch (Exception e) {
            log.error("Exception while adding mail status {}", e);
        }
        return null;
    }
    public Map<String, ?> readFile(MultipartFile file, String colWithEmails) {
        if (file.getOriginalFilename().contains(".csv")) {
            return readFromCsv(file, colWithEmails);
        } else if (file.getOriginalFilename().contains(".xls")) {
            return readFromExcel(file, colWithEmails);
        } else {
            Map<String, String> err = new HashMap<>();
            err.put("ERROR", "Please upload either CSV or Excel file");
            return err;
        }
    }
    private Map<String, List<String>> readFromCsv(MultipartFile file, String colWithEmails) {
        try {
            Map<String, List<String>> result = new HashMap<>();
            List<String> listOfEmailId = new ArrayList<>();

            EmailValidator validator = EmailValidator.getInstance();
            Reader reader = new InputStreamReader(file.getInputStream());
            CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT);
            List<CSVRecord> list = parser.getRecords();
            SortedSet<String> sortedEmailSet = new TreeSet<>();
            Set<String> junkEmails = new HashSet<>();
            for (CSVRecord record : list) {
                String[] arr = new String[record.size()];
                int i = 0;
                for (String email : record) {
                    if (validator.isValid(email)) {
                        listOfEmailId.add(email);
                    }
                }
            }
            parser.close();
            result.put("EMAILS", listOfEmailId);
            return result;
        } catch (Exception e) {
            log.error("Exception while reading from the csv file {}", e);
        }
        return Collections.EMPTY_MAP;
    }
    private Map<String, List<String>> readFromExcel(MultipartFile file, String colWithEmails) {
        try {
            Map<String, List<String>> result = new HashMap<>();
            List<String> listOfEmailId = new ArrayList<>();

            EmailValidator validator = EmailValidator.getInstance();
            String fileName = file.getOriginalFilename();
            Workbook wb = null;
            if (fileName.substring(fileName.indexOf('.')).equals("xls")) {
                wb = new HSSFWorkbook(file.getInputStream());
            } else {
                wb = new XSSFWorkbook(file.getInputStream());
            }
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() != 0) {
                for (int i = 0; i < sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    String cellVal = getCellValue(row.getCell(0));
                    if (validator.isValid(cellVal)) {
                        listOfEmailId.add(cellVal);
                    }
                }
            }
            result.put("EMAILS", listOfEmailId);
            return result;
        } catch (Exception e) {
            log.error("exception {}", e);
        }
        return Collections.EMPTY_MAP;
    }
    private String getCellValue(Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            log.error("Exception while fetching the cell value ::: {}", e);
            return "";
        }
    }
    public Object addUnsubscribedMail(UnsubscribedMails unsubscribedMail) {
        try {
            String emailId = unsubscribedMail.getEmailId();
            String decodedEmailId = new String(Base64.getDecoder().decode(emailId), StandardCharsets.UTF_8);
            Optional<UnsubscribedMails> mailObj = unsubscribedMailRepository.findByEmailId(decodedEmailId);
            if (mailObj.isPresent()) {
                log.error("Email ID {} has already been unsubscribed.", decodedEmailId);
                return new ResponseEntity<>(commonUtils.message(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Email ID has already been unsubscribed to support@wuelev8.tech"), HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                unsubscribedMail.setEmailId(decodedEmailId);
                return unsubscribedMailRepository.save(unsubscribedMail);
            }
        } catch (Exception e) {
            log.error("Error occurred while adding unsubscribed mail: {}", e);
            return new ResponseEntity<>(commonUtils.message(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Exception while adding the unsubscribed mail"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
