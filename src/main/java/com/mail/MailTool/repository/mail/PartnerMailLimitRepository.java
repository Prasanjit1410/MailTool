package com.mail.MailTool.repository.mail;

import com.mail.MailTool.domain.mail.BulkMailAttempt;
import com.mail.MailTool.domain.mail.PartnerMailLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Optional;

@EnableJpaRepositories
public interface PartnerMailLimitRepository extends JpaRepository<PartnerMailLimit, Long> {
    Optional<PartnerMailLimit> findByPartnerId(String partnerId);
}
