package com.mail.MailTool.dto;




import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Response {
    private Integer httpStatus;

    private Boolean success;

    private Date timestamp;

    private String message;

    private Object result;

    private ResponseError error;

    public Response(Integer httpStatus, Boolean success,String message, Object result, ResponseError error) {

        this.httpStatus = httpStatus;
        this.success = success;
        this.message = message;
        this.error = error;
        this.result = result;
    }

    public Date getTimestamp() {

        return Calendar.getInstance().getTime();
    }

}


