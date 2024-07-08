package com.mail.MailTool.domain.mail;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Entity
@Getter
@Setter
@ToString
@Table(name="unsubscribed_mails")
public class UnsubscribedMails {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_id", nullable = false)
    private String emailId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "createdby", length = 100)
    private String createdby;

    @Column(name = "createdts")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdts;

}
