package com.mail.MailTool.domain.mail;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;


@Entity
@Getter
@Setter
@ToString
@Table(name="partner_mail_limit")
public class PartnerMailLimit {

    @Id
    @Column(name = "limit_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long limitId;

    @Column(name = "partner_id")
    private String partnerId;

    @Column(name = "mail_limit")
    private int mailLimit;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "consumed")
    private int consumed;

    @Column(name = "createdby", length = 100)
    private String createdby;

    @Column(name = "createdts")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdts;

    @Column(name = "updatedby", length = 100)
    private String updatedby;

    @Column(name = "updatedts")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedts;
}

