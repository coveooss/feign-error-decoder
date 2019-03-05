package com.coveo.feign;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

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
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithDuplicateErrorCodeException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithInheritedExceptions;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithNoErrorCodeServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithPlainExceptions;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiClassWithSpringAnnotations;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionHardcodingDetailMessage;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsNotExtendingServiceException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsWithInvalidConstructor;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithMethodsNotAnnotated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Request;
import feign.Request.Body;
import feign.Request.HttpMethod;
import feign.Response;
import feign.codec.ErrorDecoder;

@SuppressWarnings({"resource", "unused"})
@RunWith(MockitoJUnitRunner.class)
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
    ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(
            TestApiClassWithPlainExceptions.class, fallbackErrorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    verify(fallbackErrorDecoderMock).decode(eq(""), Mockito.any(Response.class));
  }

  @Test
  public void testResponseIsBufferedOnFallback() throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(
            TestApiClassWithPlainExceptions.class, fallbackErrorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(fallbackErrorDecoderMock).decode(eq(""), responseCaptor.capture());

    assertThat(responseCaptor.getValue().body(), not(response.body()));
  }

  @Test
  public void testWithPlainExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithPlainExceptions.class);

    assertThat(
        exceptionsThrown.keySet(),
        containsInAnyOrder(
            ExceptionWithEmptyConstructorException.ERROR_CODE,
            ExceptionWithStringConstructorException.ERROR_CODE,
            ExceptionWithTwoStringsConstructorException.ERROR_CODE,
            ExceptionWithThrowableConstructorException.ERROR_CODE,
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE,
            ExceptionWithExceptionConstructorException.ERROR_CODE));
  }

  @Test
  public void testWithSpringAnnotations() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithSpringAnnotations.class);

    assertThat(
        exceptionsThrown.keySet(),
        containsInAnyOrder(
            ExceptionWithEmptyConstructorException.ERROR_CODE,
            ExceptionWithStringConstructorException.ERROR_CODE,
            ExceptionWithTwoStringsConstructorException.ERROR_CODE,
            ExceptionWithThrowableConstructorException.ERROR_CODE,
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE,
            ExceptionHardcodingDetailMessage.ERROR_CODE));
  }

  @Test
  public void testWithInheritedExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithInheritedExceptions.class);

    assertThat(
        exceptionsThrown.keySet(),
        containsInAnyOrder(
            ConcreteServiceException.ERROR_CODE, ConcreteSubServiceException.ERROR_CODE));
  }

  @Test
  public void testWithUnannotatedMethod() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithMethodsNotAnnotated.class);

    assertThat(
        exceptionsThrown.keySet(),
        containsInAnyOrder(ExceptionWithEmptyConstructorException.ERROR_CODE));
  }

  @Test
  public void testApiWithExceptionsNotExtendingServiceException() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(
            TestApiWithExceptionsNotExtendingServiceException.class);

    assertThat(exceptionsThrown.size(), is(0));
  }

  @Test
  public void testApiWithExceptionWithInvalidConstructor() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithExceptionsWithInvalidConstructor.class);

    assertThat(
        exceptionsThrown.keySet(), containsInAnyOrder(ConcreteSubServiceException.ERROR_CODE));
  }

  @Test
  public void testDecodeThrownException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
        getResponseWithErrorCode(ExceptionWithEmptyConstructorException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(ExceptionWithEmptyConstructorException.class));
    assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
  }

  @Test
  public void testDecodeThrownExceptionWithHardcodedMessage() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiWithExceptionHardcodingDetailMessage.class);
    ExceptionHardcodingDetailMessage originalException = new ExceptionHardcodingDetailMessage();

    assertThat(originalException.getMessage(), is(not(DUMMY_MESSAGE)));

    Response response =
        getResponseWithErrorCode(ExceptionHardcodingDetailMessage.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(ExceptionHardcodingDetailMessage.class));
    assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
  }

  @Test
  public void testDecodeThrownAbstractException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithInheritedExceptions.class);
    Response response =
        getResponseWithErrorCode(ConcreteServiceException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(ConcreteServiceException.class));
    assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
  }

  @Test
  public void testDecodeThrownSubAbstractException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithInheritedExceptions.class);
    Response response =
        getResponseWithErrorCode(ConcreteSubServiceException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(ConcreteSubServiceException.class));
    assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
  }

  @Test
  public void testAdditionalRuntimeException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class);
    Response response =
        getResponseWithErrorCode(AdditionalRuntimeException.ERROR_CODE, DUMMY_MESSAGE);

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(AdditionalRuntimeException.class));
    assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowOnDistinctExceptionsWithTheSameErrorCode() throws Exception {
    new ServiceExceptionErrorDecoder(TestApiClassWithDuplicateErrorCodeException.class);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowOnExceptionsWithTheNoErrorCode() throws Exception {
    new ServiceExceptionErrorDecoder(TestApiClassWithNoErrorCodeServiceException.class);
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
        .request(Request.create(HttpMethod.GET, "", new HashMap<>(), Body.empty()))
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, ThrownExceptionDetails<ServiceException>>
      getExceptionsThrownMapFromErrorDecoder(Class<?> apiInterface) throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(apiInterface);
    return (Map<String, ThrownExceptionDetails<ServiceException>>)
        EXCEPTION_THROWN_FIELD.get(errorDecoder);
  }
}
