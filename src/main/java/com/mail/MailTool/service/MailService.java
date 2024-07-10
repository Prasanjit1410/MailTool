package com.mail.MailTool.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mail.MailTool.config.AWSConfig;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
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
    AWSConfig awsConfig;

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

    @Value("${bucket}")
    private String bucketName;
    @Autowired
    AmazonS3 s3Client;

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
    public ResponseEntity<?> saveMailStatus(MailStatus mailStatus) {
        try {
            MailStatus savedStatus = mailStatusRepository.save(mailStatus);
            return new ResponseEntity<>(responseUtil.successResponse("Save mail success",String.valueOf(savedStatus)),HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Exception while adding mail status", e);
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while adding mail status"),HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    public ResponseEntity<?> readFile(MultipartFile file, String colWithEmails) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty or null");
        }

        String fileName = file.getOriginalFilename();
        try {
            if (fileName != null && fileName.endsWith(".csv")) {
                return readFromCsv(file, colWithEmails);
            } else if (fileName != null && (fileName.endsWith(".xls") || fileName.endsWith(".xlsx"))) {
                return readFromExcel(file, colWithEmails);
            } else {
                return  new ResponseEntity<>(responseUtil.badRequestResponse("Unsupported file format. Please upload CSV or Excel file."),HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception occurred while processing file:"),HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }


    private ResponseEntity<?> readFromCsv(MultipartFile file, String colWithEmails) {
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
            return new ResponseEntity<>(responseUtil.successResponse("Read from CSV",String.valueOf(listOfEmailId)),HttpStatus.OK);
        } catch (Exception e) {
            String errorMessage = "Exception while reading from the CSV file: " + e.getMessage();
            log.error(errorMessage);
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while reading from the CSV file"),HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    private ResponseEntity<?> readFromExcel(MultipartFile file, String colWithEmails) {
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
            return new ResponseEntity<>(responseUtil.successResponse("Read from Excel",String.valueOf(listOfEmailId)),HttpStatus.OK);
        } catch (Exception e) {
            log.error("exception {}", e);
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception while reading from the CSV file"),HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    private String getCellValue(Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            log.error("Exception while fetching the cell value ::: {}", e);
            return "";
        }
    }
    public ResponseEntity<?> addUnsubscribedMail(UnsubscribedMails unsubscribedMail) {
        String emailId = unsubscribedMail.getEmailId();
        String decodedEmailId = new String(Base64.getDecoder().decode(emailId), StandardCharsets.UTF_8);
        Optional<UnsubscribedMails> mailObj;
        try {
            mailObj = unsubscribedMailRepository.findByEmailId(decodedEmailId);
            if (mailObj.isPresent()) {
                log.error("Email ID {} has already been unsubscribed.", decodedEmailId);
                return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Email ID has already been unsubscribed to support@bmj.co.in"),HttpStatus.INTERNAL_SERVER_ERROR) ;
            } else {
                unsubscribedMail.setEmailId(decodedEmailId);
                UnsubscribedMails savedMail = unsubscribedMailRepository.save(unsubscribedMail);
                return new ResponseEntity<>(responseUtil.successResponse("Successfully Added", null), HttpStatus.CREATED);

            }
        } catch (Exception e) {
            log.error("Exception occurred while adding unsubscribed mail: {}", e.getMessage());
            return  new ResponseEntity<>(responseUtil.internalServerErrorResponse("Exception occurred while adding unsubscribed mail"),HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<?> uploadFileToS3(MultipartFile file) {

        try{
            String key = "uploads/" + new Date().getTime() + "-" + Objects.requireNonNull(file.getOriginalFilename()).replaceAll("[^A-Za-z0-9]+", "-").replaceAll("\\s","-");

            s3Client.putObject(new PutObjectRequest(bucketName, key, file.getInputStream(), null));
            // Upload file to S3 bucket
            log.info("File uploaded to S3 with key: {}", key);

            return new ResponseEntity<>(responseUtil.successResponse("Successfully Upload to S3",String.valueOf(s3Client.getUrl(bucketName, key))), HttpStatus.OK);
        }
        catch(Exception e){
            log.error("Error occured while upload on s3 file:{}",e.getMessage());
            return new ResponseEntity<>(responseUtil.internalServerErrorResponse("Error occured while upload on s3 file"),HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
