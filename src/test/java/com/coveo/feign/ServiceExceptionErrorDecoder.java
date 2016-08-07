package com.coveo.feign;

import feign.Response;
import feign.codec.ErrorDecoder;

public class ServiceExceptionErrorDecoder
    extends ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> {
  private ErrorDecoder fallbackErrorDecoder = new ErrorDecoder.Default();

  public ServiceExceptionErrorDecoder(Class<?> apiClass) {
    super(apiClass, ErrorCodeAndMessage.class, ServiceException.class, "com.coveo.feign");
  }

  @Override
  protected Exception getFallbackException(String methodKey, Response response) {
    return fallbackErrorDecoder.decode(methodKey, response);
  }

  @Override
  protected String getKeyFromException(ServiceException exception) {
    return exception.getErrorCode();
  }

  @Override
  protected String getKeyFromResponse(ErrorCodeAndMessage apiResponse) {
    return apiResponse.getErrorCode();
  }

  @Override
  protected String getMessageFromResponse(ErrorCodeAndMessage apiResponse) {
    return apiResponse.getMessage();
  }
}
