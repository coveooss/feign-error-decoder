package com.coveo.feign;

import java.util.Map;
import static com.coveo.feign.ReflectionErrorDecoderTestClasses.*;

import feign.codec.ErrorDecoder;

public class ServiceExceptionErrorDecoder
    extends ReflectionErrorDecoder<ErrorCodeAndMessage, BaseServiceException> {

  public ServiceExceptionErrorDecoder(Class<?> apiClass) {
    super(apiClass, ErrorCodeAndMessage.class, BaseServiceException.class, "com.coveo.feign");
  }

  public ServiceExceptionErrorDecoder(Class<?> apiClass, ErrorDecoder fallbackErrorDecoder) {
    super(apiClass, ErrorCodeAndMessage.class, BaseServiceException.class, "com.coveo.feign");
    setFallbackErrorDecoder(fallbackErrorDecoder);
  }

  @Override
  protected String getKeyFromException(BaseServiceException exception) {
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
        AdditionalRuntimeException.ERROR_CODE,
        new ThrownExceptionDetails<RuntimeException>()
            .withClazz(AdditionalRuntimeException.class)
            .withExceptionSupplier(AdditionalRuntimeException::new));
    runtimeExceptionsThrown.put(
            AdditionalNotInterfacedRuntimeException.ERROR_CODE,
            new ThrownExceptionDetails<RuntimeException>()
                    .withClazz(AdditionalNotInterfacedRuntimeException.class)
                    .withExceptionSupplier(AdditionalNotInterfacedRuntimeException::new));
  }
}
