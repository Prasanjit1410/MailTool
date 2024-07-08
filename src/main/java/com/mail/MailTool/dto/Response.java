package com.mail.MailTool.dto;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class Response {

    private int responseCode;
    private String message;
    private Object data;

    public Response() {

    }

    public Response(int responseCode, String message, Object data) {
        this.responseCode = responseCode;
        this.message = message;
        this.data = data;
    }
}
