package com.mail.MailTool.domain.mail;

import java.util.Date;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@Table(name="blocked_email")
public class BlockedEmail {

    @Id
    @Column(name = "blocked_mail_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long blockedMailId;

    @Column(name = "blocked_email", nullable = false)
    private String blockedEmail;

    @Column(name = "createdby", length = 100)
    private String createdby;

    @Column(name = "createdts")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdts;

}