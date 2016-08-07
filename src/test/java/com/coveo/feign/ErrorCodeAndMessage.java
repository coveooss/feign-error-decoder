package com.coveo.feign;

public class ErrorCodeAndMessage {
  private String message;
  private String errorCode;

  public String getErrorCode() {
    return errorCode;
  }

  public String getMessage() {
    return message;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorCodeAndMessage withErrorCode(String errorCode) {
    setErrorCode(errorCode);
    return this;
  }

  public ErrorCodeAndMessage withMessage(String message) {
    this.message = message;
    return this;
  }
}
