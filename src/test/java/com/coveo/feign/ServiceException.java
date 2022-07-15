package com.coveo.feign;

import com.coveo.feign.annotation.ExceptionMessageSetter;

public abstract class ServiceException extends BaseServiceException implements ExceptionMessageSetter {
  private static final long serialVersionUID = 4116691862956368612L;
  private String detailMessage;

  protected ServiceException(String errorCode) {
    super(errorCode);
  }

  protected ServiceException(String errorCode, Throwable e) {
    super(errorCode, e);
  }

  protected ServiceException(String errorCode, String message) {
    super(errorCode, message);
  }

  protected ServiceException(String errorCode, String message, Throwable innerException) {
    super(errorCode, message, innerException);
  }

  @Override
  public void setExceptionMessage(String detailMessage) {
    this.detailMessage = detailMessage;
  }

  @Override
  public String getMessage() {
    return detailMessage == null ? super.getMessage() : detailMessage;
  }
}
