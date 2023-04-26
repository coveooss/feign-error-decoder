package com.coveo.feign;

public abstract class BaseServiceException extends Exception {
  private static final long serialVersionUID = 4116691862956368612L;
  private final String errorCode;

  protected BaseServiceException(String errorCode) {
    this.errorCode = errorCode;
  }

  protected BaseServiceException(String errorCode, Throwable e) {
    super(e);
    this.errorCode = errorCode;
  }

  protected BaseServiceException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  protected BaseServiceException(String errorCode, String message, Throwable innerException) {
    super(message, innerException);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
