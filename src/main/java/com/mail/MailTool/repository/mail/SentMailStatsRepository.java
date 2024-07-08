package com.mail.MailTool.repository.mail;


import com.mail.MailTool.domain.mail.MailStatus;
import com.mail.MailTool.domain.mail.SentMailStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

@EnableJpaRepositories
public interface SentMailStatsRepository extends JpaRepository<SentMailStats,Long> {

    List<SentMailStats> findByMailCampaignIdIn(List<String> campaignWithSuccessfulMailSent);
}
