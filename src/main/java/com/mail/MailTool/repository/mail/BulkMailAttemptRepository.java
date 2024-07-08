package com.mail.MailTool.repository.mail;

import com.mail.MailTool.domain.mail.BulkMailAttempt;
import com.mail.MailTool.domain.mail.MailStatus;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories
public interface BulkMailAttemptRepository extends JpaRepository<BulkMailAttempt, Long>, JpaSpecificationExecutor<BulkMailAttempt> {

    List<BulkMailAttempt> findAllByCampaignId(String campaignId);

    List<BulkMailAttempt> findByPartnerId(String partnerId);

    BulkMailAttempt findByCampaignId(String campaignId);
}