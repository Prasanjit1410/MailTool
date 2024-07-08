package com.mail.MailTool.repository.mail;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.mail.MailTool.domain.mail.UnsubscribedMails;

@EnableJpaRepositories
public interface UnsubscribedMailRepository extends JpaRepository <UnsubscribedMails,Long> {

    Optional<UnsubscribedMails> findByEmailId(String emailId);

}
