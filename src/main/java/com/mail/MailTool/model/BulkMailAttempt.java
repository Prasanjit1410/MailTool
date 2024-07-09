package com.mail.MailTool.model;



import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
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
@Table(name="bulk_mail_attempt")
public class BulkMailAttempt {

    @Id
    @Column(name = "sent_mail_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sentMailId;

    @Lob
    @Column(name = "recipient_list_to")
    private String recipientListTo;

    @Column(name = "recipient_list_cc")
    private String recipientListCc;

    @Column(name = "recipient_list_bcc")
    private String recipientListBcc;

    @Column(name = "sent_dt")
    private LocalDateTime sentDt;

    @Column(name = "mail_category_id")
    private Long mailCategoryId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "u_id", nullable = false)
    private String uId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "subject")
    private String subject;

    @Lob
    @Column(name = "mail_content", nullable = false)
    private String mailContent;


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

    @Column(name = "is_scheduled_mail",columnDefinition = "boolean default false")
    private boolean isScheduledMail;

    @Column(name = "is_mail_sent",columnDefinition = "boolean default false")
    private boolean isMailSent;

    @Column(name = "platform")
    private String platform;

    @Column(name = "is_scheduled_mail_cancelled",columnDefinition = "boolean default false")
    private boolean isScheduledMailCancelled;

//    @Transient
//    private SentMailStats sentMailStatsDetails;
}

