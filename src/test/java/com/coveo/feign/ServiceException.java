package com.coveo.feign;

import com.coveo.feign.annotation.ExceptionMessageSetter;

public abstract class ServiceException extends Exception implements ExceptionMessageSetter {
  private static final long serialVersionUID = 4116691862956368612L;
  private String errorCode;
  private String detailMessage;

  protected ServiceException(String errorCode) {
    this.errorCode = errorCode;
  }

  protected ServiceException(String errorCode, Throwable e) {
    super(e);
    this.errorCode = errorCode;
  }

  protected ServiceException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  protected ServiceException(String errorCode, String message, Throwable innerException) {
    super(message, innerException);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String getMessage() {
    return detailMessage == null ? super.getMessage() : detailMessage;
  }

  @Override
  public void setExceptionMessage(String detailMessage) {
    this.detailMessage = detailMessage;
  }
}
