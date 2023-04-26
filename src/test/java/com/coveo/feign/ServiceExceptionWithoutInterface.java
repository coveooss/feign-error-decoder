package com.coveo.feign;

public abstract class ServiceExceptionWithoutInterface extends BaseServiceException {
  private static final long serialVersionUID = 4116691862956368612L;

  protected ServiceExceptionWithoutInterface(String errorCode) {
    super(errorCode);
  }
}
