package com.mail.MailTool.util;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class InternityUser {
    private String userToken;
    private String userName;
    private String uId;
}
