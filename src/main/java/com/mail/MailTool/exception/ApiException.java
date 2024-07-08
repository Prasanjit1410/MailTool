package com.mail.MailTool.exception;



import org.springframework.http.HttpStatus;

public class ApiException extends Exception {

    private static final long serialVersionUID = 158487L;

    private final String message;
    private final HttpStatus httpStatus;
    private final Integer responseCode;

    public ApiException(Integer responseCode, String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
        this.responseCode = responseCode;
    }

    // Getter and setters
    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Integer getResponseCode() { return responseCode; }
}
