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
@Table(name="mail_status",uniqueConstraints = { @UniqueConstraint(columnNames = { "status","receiver_email","campaign_id" }) })
public class MailStatus {

    @Id
    @Column(name = "mail_status_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mailStatusId;

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "receiver_email", nullable = false)
    private String receiverEmail;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "bounce_type")
    private String bounceType;

    @Column(name = "smtp_response")
    private String smtpResponse;

    @Column(name = "createdby", length = 100)
    private String createdby;

    @Column(name = "createdts")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdts;

}

