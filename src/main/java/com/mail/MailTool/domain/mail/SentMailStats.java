package com.mail.MailTool.domain.mail;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.util.Date;


@Entity
@Getter
@Setter
@ToString
@Table(name="sent_mail_stats")
public class SentMailStats {

    @Id
    @Column(name = "sent_mail_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sentMailId;

    @Column(name = "mail_campaign_id", nullable = false)
    private String mailCampaignId;

    @Lob
    @Column(name = "sent_email")
    private String sentEmail;

    @Lob
    @Column(name = "blocked_email")
    private String blockedEmail;

    @Column(name = "sent_email_numb")
    private int sentEmailNumb;

    @Column(name = "blocked_email_numb")
    private int blockedEmailNumb;

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

