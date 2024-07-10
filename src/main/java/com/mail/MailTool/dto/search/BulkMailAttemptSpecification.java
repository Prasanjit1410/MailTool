package com.mail.MailTool.dto.search;
import com.mail.MailTool.custom.specification.SearchBulkMailsCriteria;
import com.mail.MailTool.domain.mail.BulkMailAttempt;
import com.mail.MailTool.repository.mail.BulkMailAttemptRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@NoArgsConstructor
@Slf4j
public  class BulkMailAttemptSpecification implements Specification<BulkMailAttempt> {

    SearchBulkMailsCriteria searchCriteria;
    BulkMailAttemptRepository bulkMailAttemptRepository;

    public BulkMailAttemptSpecification(SearchBulkMailsCriteria searchCriteria, BulkMailAttemptRepository bulkMailAttemptRepository) {
        this.searchCriteria = searchCriteria;
        this.bulkMailAttemptRepository = bulkMailAttemptRepository;
    }

    @Override
    public jakarta.persistence.criteria.Predicate toPredicate(Root<BulkMailAttempt> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();

        if (!"NA".equalsIgnoreCase(searchCriteria.getCampaignId())) {
            predicates.add(criteriaBuilder.equal(root.get("campaignId"), searchCriteria.getCampaignId()));
        }
        if (!"NA".equalsIgnoreCase(searchCriteria.getStartDate())) {
            String formattedDate = searchCriteria.getStartDate().replaceAll("\\s+", "").replace('/', '-').replaceAll("(?<=\\b|\\D)(\\d)(?=\\b|\\D)", "0$1");
            log.info("formattedStartDate: {}", formattedDate);
            if (formattedDate.length() > 10) {
                formattedDate = formattedDate.substring(0, 10);

            }
            if (formattedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate startDate = LocalDate.parse(formattedDate);
                LocalDateTime startDateTime = startDate.atStartOfDay();
                log.info("startDateTime: {}", startDateTime);
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("sentDt"), startDateTime));
            } else {
                log.warn("Invalid date format: {}", formattedDate);
            }
        }
        if (!"NA".equalsIgnoreCase(searchCriteria.getEndDate())) {
            String formattedDate = searchCriteria.getEndDate().replaceAll("\\s+", "").replace('/', '-').replaceAll("(?<=\\b|\\D)(\\d)(?=\\b|\\D)", "0$1");
            log.info("formattedEndDate: {}", formattedDate);
            if (formattedDate.length() > 10) {
                formattedDate = formattedDate.substring(0, 10);
            }
            if (formattedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate endDate = LocalDate.parse(formattedDate);
                LocalDateTime endDateTime = endDate.atTime(23, 59, 59); // Set the time to 23:59:59 for end of the day
                log.info("endDateTime: {}", endDateTime);
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("sentDt"), endDateTime));
            } else {
                log.warn("Invalid date format: {}", formattedDate);
            }
        }
        if (!"NA".equalsIgnoreCase(searchCriteria.getUId())) {
            predicates.add(criteriaBuilder.equal(root.get("uId"), searchCriteria.getUId()));
        }
        if (!"NA".equalsIgnoreCase(searchCriteria.getPlatform())) {
            predicates.add(criteriaBuilder.equal(root.get("platform"), searchCriteria.getPlatform().toUpperCase()));
        }
        if (searchCriteria.isMailSent()) {
            predicates.add(criteriaBuilder.equal(root.get("isMailSent"), true));
        }
        if (searchCriteria.isScheduled()) {
            predicates.add(criteriaBuilder.equal(root.get("isScheduledMail"), true));
        }
        if (searchCriteria.isCancelled()) {
            predicates.add(criteriaBuilder.equal(root.get("isScheduledMailCancelled"), true));
        }

        Predicate[] predicateArr = new Predicate[predicates.size()];
        return criteriaBuilder.and(predicates.toArray(predicateArr));
    }


}