package com.coveo.feign;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coveo.feign.ReflectionErrorDecoderTestClasses.AdditionalRuntimeException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ConcreteServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ConcreteSubServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionHardcodingDetailMessage;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithEmptyConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithExceptionConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithStringAndThrowableConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithStringConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithThrowableConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ExceptionWithTwoStringsConstructorException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.MultipleConstructorsException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.MultipleConstructorsWithOnlyThrowableArgumentsException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithDuplicateErrorCodeException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithInheritedExceptions;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithNoErrorCodeServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithPlainExceptions;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithSpringAnnotations;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionHardcodingDetailMessage;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsNotExtendingServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsWithInvalidConstructor;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsWithMultipleConstructors;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsWithMultipleConstructorsWithOnlyThrowables;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithMethodsNotAnnotated;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.ConcreteSubServiceExceptionWithoutInterface;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.AdditionalNotInterfacedRuntimeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Request;
import feign.Request.Body;
import feign.Request.HttpMethod;
import feign.Response;
import feign.codec.ErrorDecoder;

@SuppressWarnings({"resource", "unused"})
@ExtendWith(MockitoExtension.class)
public class ReflectionErrorDecoderTest {
  private static final String DUMMY_MESSAGE = "dummy message";
  private static final Field EXCEPTION_THROWN_FIELD;

  static {
    try {
      EXCEPTION_THROWN_FIELD = ReflectionErrorDecoder.class.getDeclaredField("exceptionsThrown");
      EXCEPTION_THROWN_FIELD.setAccessible(true);
    } catch (NoSuchFieldException | SecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @Mock private ErrorDecoder fallbackErrorDecoderMock;

  @Test
  public void testFallbackOnUnknownException() throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, BaseServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(
            TestApiClassWithPlainExceptions.class, fallbackErrorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    verify(fallbackErrorDecoderMock).decode(eq(""), Mockito.any(Response.class));
  }

  @Test
  public void testResponseIsBufferedOnFallback() throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, BaseServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(
            TestApiClassWithPlainExceptions.class, fallbackErrorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(fallbackErrorDecoderMock).decode(eq(""), responseCaptor.capture());

    assertThat(responseCaptor.getValue().body()).isNotEqualTo(response.body());
  }

  @Test
  public void testWithPlainExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithPlainExceptions.class);

    assertThat(exceptionsThrown.keySet())
        .containsExactly(
            ExceptionWithEmptyConstructorException.ERROR_CODE,
            ExceptionWithStringConstructorException.ERROR_CODE,
            ExceptionWithTwoStringsConstructorException.ERROR_CODE,
            ExceptionWithThrowableConstructorException.ERROR_CODE,
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE,
            ExceptionWithExceptionConstructorException.ERROR_CODE,
            ConcreteSubServiceExceptionWithoutInterface.ERROR_CODE);
  }

  @Test
  public void testWithSpringAnnotations() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithSpringAnnotations.class);

    assertThat(exceptionsThrown.keySet())
        .containsExactly(
            ExceptionWithEmptyConstructorException.ERROR_CODE,
            ExceptionWithStringConstructorException.ERROR_CODE,
            ExceptionWithTwoStringsConstructorException.ERROR_CODE,
            ExceptionWithThrowableConstructorException.ERROR_CODE,
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE,
            ExceptionHardcodingDetailMessage.ERROR_CODE);
  }

  @Test
  public void testWithInheritedExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithInheritedExceptions.class);

    assertThat(exceptionsThrown.keySet())
        .containsExactly(
            ConcreteServiceException.ERROR_CODE, ConcreteSubServiceException.ERROR_CODE);
  }

  @Test
  public void testWithUnannotatedMethod() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithMethodsNotAnnotated.class);

    assertThat(exceptionsThrown.keySet())
        .containsExactly(ExceptionWithEmptyConstructorException.ERROR_CODE);
  }

  @Test
  public void testApiWithExceptionsNotExtendingServiceException() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(
            TestApiWithExceptionsNotExtendingServiceException.class);

    assertThat(exceptionsThrown.size()).isEqualTo(0);
  }

  @Test
  public void testApiWithExceptionWithInvalidConstructor() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithExceptionsWithInvalidConstructor.class);

    assertThat(exceptionsThrown.keySet()).containsExactly(ConcreteSubServiceException.ERROR_CODE);
  }

  @Test
  public void testDecodeThrownException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
        getResponseWithErrorCode(ExceptionWithEmptyConstructorException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ExceptionWithEmptyConstructorException.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  public void testDecodeThrownExceptionWithHardcodedMessage() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiWithExceptionHardcodingDetailMessage.class);
    ExceptionHardcodingDetailMessage originalException = new ExceptionHardcodingDetailMessage();

    assertThat(originalException.getMessage()).isNotEqualTo(DUMMY_MESSAGE);

    Response response =
        getResponseWithErrorCode(ExceptionHardcodingDetailMessage.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ExceptionHardcodingDetailMessage.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  public void testDecodeThrownAbstractException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithInheritedExceptions.class);
    Response response =
        getResponseWithErrorCode(ConcreteServiceException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ConcreteServiceException.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  public void testDecodeThrownSubAbstractException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithInheritedExceptions.class);
    Response response =
        getResponseWithErrorCode(ConcreteSubServiceException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ConcreteSubServiceException.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  @EnabledForJreRange(max=JRE.JAVA_15)
  public void testDecodeThrownSubAbstractExceptionWithoutInterface() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
            new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
            getResponseWithErrorCode(ConcreteSubServiceExceptionWithoutInterface.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ConcreteSubServiceExceptionWithoutInterface.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  @EnabledForJreRange(min=JRE.JAVA_16)
  public void testDecodeThrownSubAbstractExceptionWithoutInterfaceShouldDoNothing() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
            new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
            getResponseWithErrorCode(ConcreteSubServiceExceptionWithoutInterface.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(ConcreteSubServiceExceptionWithoutInterface.class);
    assertThat(exception.getMessage()).isNull();
  }

  @Test
  public void testBestConstructorIsSelected() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithExceptionsWithMultipleConstructors.class);

    assertThat(exceptionsThrown.keySet()).containsExactly(MultipleConstructorsException.ERROR_CODE);
    ThrownExceptionDetails<ServiceException> thrownExceptionDetails =
        exceptionsThrown.get(MultipleConstructorsException.ERROR_CODE);
    MultipleConstructorsException exception =
        (MultipleConstructorsException) thrownExceptionDetails.instantiate();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  public void testBestConstructorIsSelectedWithOnlyThrowablesArgumentConstructors()
      throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(
            TestApiWithExceptionsWithMultipleConstructorsWithOnlyThrowables.class);

    assertThat(exceptionsThrown.keySet())
        .containsExactly(MultipleConstructorsWithOnlyThrowableArgumentsException.ERROR_CODE);
    ThrownExceptionDetails<ServiceException> thrownExceptionDetails =
        exceptionsThrown.get(MultipleConstructorsWithOnlyThrowableArgumentsException.ERROR_CODE);
    MultipleConstructorsWithOnlyThrowableArgumentsException exception =
        (MultipleConstructorsWithOnlyThrowableArgumentsException)
            thrownExceptionDetails.instantiate();
    assertThat(exception.getCause()).isNotNull();
  }

  @Test
  public void testAdditionalRuntimeException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
        getResponseWithErrorCode(AdditionalRuntimeException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(AdditionalRuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  @EnabledForJreRange(max=JRE.JAVA_15)
  public void testAdditionalRuntimeExceptionWithoutInterface() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
            new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
            getResponseWithErrorCode(AdditionalNotInterfacedRuntimeException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(AdditionalNotInterfacedRuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo(DUMMY_MESSAGE);
  }

  @Test
  @EnabledForJreRange(min=JRE.JAVA_16)
  public void testAdditionalRuntimeExceptionWithoutInterfaceShouldDoNothing() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
            new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
            getResponseWithErrorCode(AdditionalNotInterfacedRuntimeException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception).isInstanceOf(AdditionalNotInterfacedRuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo(AdditionalNotInterfacedRuntimeException.ERROR_MESSAGE);
  }

  @Test
  public void shouldThrowOnDistinctExceptionsWithTheSameErrorCode() throws Exception {
    assertThrows(
        IllegalStateException.class,
        () -> new ServiceExceptionErrorDecoder(TestApiClassWithDuplicateErrorCodeException.class));
  }

  @Test
  public void shouldThrowOnExceptionsWithTheNoErrorCode() throws Exception {
    assertThrows(
        IllegalStateException.class,
        () -> new ServiceExceptionErrorDecoder(TestApiClassWithNoErrorCodeServiceException.class));
  }

  private Response getResponseWithErrorCode(String errorCode, String message)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    return Response.builder()
        .status(400)
        .reason("")
        .headers(new HashMap<>())
        .body(
            objectMapper.writeValueAsString(
                new ErrorCodeAndMessage().withErrorCode(errorCode).withMessage(message)),
            StandardCharsets.UTF_8)
        .request(Request.create(HttpMethod.GET, "", new HashMap<>(), Body.empty(), null))
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, ThrownExceptionDetails<ServiceException>>
      getExceptionsThrownMapFromErrorDecoder(Class<?> apiInterface) throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, BaseServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(apiInterface);
    return (Map<String, ThrownExceptionDetails<ServiceException>>)
        EXCEPTION_THROWN_FIELD.get(errorDecoder);
  }
}
