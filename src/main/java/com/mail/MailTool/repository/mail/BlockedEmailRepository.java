package com.mail.MailTool.repository.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.mail.MailTool.domain.mail.BlockedEmail;

@EnableJpaRepositories
public interface BlockedEmailRepository extends JpaRepository<BlockedEmail,Long>{

}
