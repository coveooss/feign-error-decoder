package com.coveo.feign;

import java.util.Map;

import feign.codec.ErrorDecoder;

public class ServiceExceptionErrorDecoder
    extends ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> {

  public ServiceExceptionErrorDecoder(Class<?> apiClass) {
    super(apiClass, ErrorCodeAndMessage.class, ServiceException.class, "com.coveo.feign");
  }

  public ServiceExceptionErrorDecoder(Class<?> apiClass, ErrorDecoder fallbackErrorDecoder) {
    super(apiClass, ErrorCodeAndMessage.class, ServiceException.class, "com.coveo.feign");
    setFallbackErrorDecoder(fallbackErrorDecoder);
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

  @Override
  protected void addAdditionalRuntimeExceptions(
      Map<String, ThrownExceptionDetails<RuntimeException>> runtimeExceptionsThrown) {
    runtimeExceptionsThrown.put(
        ReflectionErrorDecoderTest.AdditionalRuntimeException.ERROR_CODE,
        new ThrownExceptionDetails<RuntimeException>()
            .withClazz(ReflectionErrorDecoderTest.AdditionalRuntimeException.class)
            .withExceptionSupplier(
                () -> new ReflectionErrorDecoderTest.AdditionalRuntimeException()));
  }
}
