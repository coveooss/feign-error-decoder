package com.coveo.feign;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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
import com.coveo.feign.ReflectionErrorDecoderTestClasses.MultipleConstructorsException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.MultipleConstructorsWithOnlyThrowableArgumentsException;
import com.coveo.feign.ReflectionErrorDecoderTestClasses.MultipleFieldsException;
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
import com.coveo.feign.ReflectionErrorDecoderTestClasses.TestApiWithExceptionsWithMultipleFields;
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
  public void testBestConstructorIsSelected() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithExceptionsWithMultipleConstructors.class);

    assertThat(
        exceptionsThrown.keySet(), containsInAnyOrder(MultipleConstructorsException.ERROR_CODE));
    ThrownExceptionDetails<ServiceException> thrownExceptionDetails =
        exceptionsThrown.get(MultipleConstructorsException.ERROR_CODE);
    MultipleConstructorsException exception =
        (MultipleConstructorsException) thrownExceptionDetails.instantiate();
    assertThat(exception.getCause(), is(nullValue()));
  }

  @Test
  public void testBestConstructorIsSelectedWithOnlyThrowablesArgumentConstructors()
      throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(
            TestApiWithExceptionsWithMultipleConstructorsWithOnlyThrowables.class);

    assertThat(
        exceptionsThrown.keySet(),
        containsInAnyOrder(MultipleConstructorsWithOnlyThrowableArgumentsException.ERROR_CODE));
    ThrownExceptionDetails<ServiceException> thrownExceptionDetails =
        exceptionsThrown.get(MultipleConstructorsWithOnlyThrowableArgumentsException.ERROR_CODE);
    MultipleConstructorsWithOnlyThrowableArgumentsException exception =
        (MultipleConstructorsWithOnlyThrowableArgumentsException)
            thrownExceptionDetails.instantiate();
    assertThat(exception.getCause(), is(not(nullValue())));
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

  @Test
  public void testMultipleFieldsException() throws Exception {
    ServiceExceptionErrorDecoder errorDecoder =
            new ServiceExceptionErrorDecoder(TestApiWithExceptionsWithMultipleFields.class);
    errorDecoder.setMultipleFieldsEnabled(true);

    Map<String, Object> responseAsMap = new HashMap<>();
    responseAsMap.put("errorCode", MultipleFieldsException.ERROR_CODE);
    responseAsMap.put("message", DUMMY_MESSAGE);
    responseAsMap.put("field1", 1);
    responseAsMap.put("field2", 1.0);
    responseAsMap.put("field3", "hello world");
    responseAsMap.put("field4", Arrays.asList("hello", "world"));
    responseAsMap.put("field5", Collections.singletonMap("key", "value"));

    ObjectMapper objectMapper = new ObjectMapper();
    Response response = Response.builder()
            .status(400)
            .reason("")
            .headers(new HashMap<>())
            .body(objectMapper.writeValueAsString(responseAsMap), StandardCharsets.UTF_8)
            .request(Request.create(HttpMethod.GET, "", new HashMap<>(), Body.empty()))
            .build();

    Exception exception = errorDecoder.decode("", response);

    assertThat(exception, instanceOf(MultipleFieldsException.class));

    MultipleFieldsException multipleFieldsException = (MultipleFieldsException) exception;
    assertThat(multipleFieldsException.getMessage(), is(DUMMY_MESSAGE));
    assertThat(multipleFieldsException.getField1(), is(responseAsMap.get("field1")));
    assertThat(multipleFieldsException.getField2(), is(responseAsMap.get("field2")));
    assertThat(multipleFieldsException.getField3(), is(responseAsMap.get("field3")));
    assertThat(multipleFieldsException.getField4(), is(responseAsMap.get("field4")));
    assertThat(multipleFieldsException.getField5(), is(responseAsMap.get("field5")));
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
