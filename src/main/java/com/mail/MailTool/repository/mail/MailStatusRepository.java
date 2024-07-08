package com.mail.MailTool.repository.mail;

import com.mail.MailTool.domain.mail.BulkMailAttempt;
import com.mail.MailTool.domain.mail.MailStatus;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories
public interface MailStatusRepository extends JpaRepository<MailStatus,Long> {

    List<MailStatus> findByCampaignId(String campaignId);

    List<MailStatus> findByStatus(String string);

}

