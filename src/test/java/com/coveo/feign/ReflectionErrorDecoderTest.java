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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.RequestLine;
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

  @Mock private ErrorDecoder errorDecoderMock;

  @Test
  public void testFallbackOnUnknownException() throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class, errorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    verify(errorDecoderMock).decode(eq(""), Mockito.any(Response.class));
  }

  @Test
  public void testResponseIsBufferedOnFallback() throws Exception {
    ReflectionErrorDecoder<ErrorCodeAndMessage, ServiceException> errorDecoder =
        new ServiceExceptionErrorDecoder(TestApiClassWithPlainExceptions.class, errorDecoderMock);
    Response response = getResponseWithErrorCode(UUID.randomUUID().toString(), DUMMY_MESSAGE);

    errorDecoder.decode("", response);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(errorDecoderMock).decode(eq(""), responseCaptor.capture());

    assertThat(responseCaptor.getValue().body(), not(response.body()));
  }

  @Test
  public void testWithPlainExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithPlainExceptions.class);

    assertThat(exceptionsThrown.size(), is(5));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithEmptyConstructorException.ERROR_CODE));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithStringConstructorException.ERROR_CODE));
    assertTrue(
        exceptionsThrown.containsKey(ExceptionWithTwoStringsConstructorException.ERROR_CODE));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithThrowableConstructorException.ERROR_CODE));
    assertTrue(
        exceptionsThrown.containsKey(
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE));
  }

  @Test
  public void testWithSpringAnnotations() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithSpringAnnotations.class);

    assertThat(exceptionsThrown.size(), is(6));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithEmptyConstructorException.ERROR_CODE));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithStringConstructorException.ERROR_CODE));
    assertTrue(
        exceptionsThrown.containsKey(ExceptionWithTwoStringsConstructorException.ERROR_CODE));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithThrowableConstructorException.ERROR_CODE));
    assertTrue(
        exceptionsThrown.containsKey(
            ExceptionWithStringAndThrowableConstructorException.ERROR_CODE));
  }

  @Test
  public void testWithInheritedExceptions() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiClassWithInheritedExceptions.class);

    assertThat(exceptionsThrown.size(), is(2));
    assertTrue(exceptionsThrown.containsKey(ConcreteServiceException.ERROR_CODE));
    assertTrue(exceptionsThrown.containsKey(ConcreteSubServiceException.ERROR_CODE));
  }

  @Test
  public void testWithUnannotatedMethod() throws Exception {
    Map<String, ThrownExceptionDetails<ServiceException>> exceptionsThrown =
        getExceptionsThrownMapFromErrorDecoder(TestApiWithMethodsNotAnnotated.class);

    assertThat(exceptionsThrown.size(), is(1));
    assertTrue(exceptionsThrown.containsKey(ExceptionWithEmptyConstructorException.ERROR_CODE));
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

    assertThat(exceptionsThrown.size(), is(1));
    assertTrue(exceptionsThrown.containsKey(ConcreteSubServiceException.ERROR_CODE));
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

  // Test classes

  private static interface TestApiWithExceptionsNotExtendingServiceException {
    @RequestLine(value = "")
    void methodWithEmptyConstructorException() throws Exception;
  }

  private static interface TestApiWithExceptionHardcodingDetailMessage {
    @RequestLine(value = "")
    void methodHardcodedDetailMessageException() throws ExceptionHardcodingDetailMessage;
  }

  private static interface TestApiWithExceptionsWithInvalidConstructor {
    @RequestLine(value = "")
    void methodWithInvalidConstructor()
        throws ConcreteSubServiceException, ExceptionWithInvalidConstructorException;
  }

  private static interface TestApiWithMethodsNotAnnotated {
    @RequestLine(value = "")
    void methodWithEmptyConstructorException() throws ExceptionWithEmptyConstructorException;

    void methodNotAnnotated() throws ConcreteSubServiceException;
  }

  private static interface TestApiClassWithPlainExceptions {
    @RequestLine(value = "")
    void methodWithEmptyConstructorException() throws ExceptionWithEmptyConstructorException;

    @RequestLine("")
    void methodWithStringConstructorException() throws ExceptionWithStringConstructorException;

    @RequestLine("")
    void methodWithTwoStringsConstructorException()
        throws ExceptionWithTwoStringsConstructorException;

    @RequestLine("")
    void methodWithThrowableConstructorException()
        throws ExceptionWithThrowableConstructorException;

    @RequestLine("")
    void methodWithStringAndThrowableConstructorException()
        throws ExceptionWithStringAndThrowableConstructorException;

    @RequestLine("")
    void anotherMethodWithStringAndThrowableConstructorException()
        throws ExceptionWithStringAndThrowableConstructorException;

    @RequestMapping("")
    void methodWithRequestMappingAndStringConstructorException()
        throws ExceptionWithStringConstructorException;

    @GetMapping("")
    void methodWithGetMappingAndStringConstructorException()
        throws ExceptionWithStringConstructorException;
  }

  private static interface TestApiClassWithSpringAnnotations {
    @RequestMapping("")
    void methodWithRequestMappingAnnotation()
        throws ExceptionWithStringAndThrowableConstructorException;

    @GetMapping("")
    void methodWithGetMappingAnnotation() throws ExceptionHardcodingDetailMessage;

    @PostMapping(value = "")
    void methodWithPostMappingAnnotation() throws ExceptionWithEmptyConstructorException;

    @PutMapping("")
    void methodWithPutMappingAnnotation() throws ExceptionWithStringConstructorException;

    @DeleteMapping("")
    void methodWithDeleteMappingAnnotation() throws ExceptionWithTwoStringsConstructorException;

    @PatchMapping("")
    void methodWithPatchMappingAnnotation() throws ExceptionWithThrowableConstructorException;
  }

  private static interface TestApiClassWithInheritedExceptions {
    @RequestLine("")
    void methodWithAbstractException() throws AbstractServiceException;
  }

  private static interface TestApiClassWithDuplicateErrorCodeException {
    @RequestLine("")
    void methodWithDuplicateErrorCodeException()
        throws ConcreteSubServiceException, DuplicateErrorCodeServiceException;
  }

  private static interface TestApiClassWithNoErrorCodeServiceException {
    @RequestLine("")
    void methodWithEmptyErrorCodeException() throws NoErrorCodeServiceException;
  }

  private static class ExceptionHardcodingDetailMessage extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "HARDCORE!!!";

    public ExceptionHardcodingDetailMessage() {
      super(ERROR_CODE, "THIS IS HARDCODED!");
    }
  }

  public static class AdditionalRuntimeException extends AbstractAdditionalRuntimeException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "RUNTIME_BSOD?";
    public static final String ERROR_MESSAGE = "PANIC";

    public AdditionalRuntimeException() {
      super(ERROR_CODE, ERROR_MESSAGE);
    }
  }

  public abstract static class AbstractAdditionalRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private String errorCode;

    protected AbstractAdditionalRuntimeException(String errorCode, String message) {
      super(message);
      this.errorCode = errorCode;
    }

    public String getErrorCode() {
      return this.errorCode;
    }
  }

  private static class ExceptionWithEmptyConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "BSOD";

    public ExceptionWithEmptyConstructorException() {
      super(ERROR_CODE);
    }
  }

  private static class ExceptionWithStringConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC";

    public ExceptionWithStringConstructorException(String useless) {
      super(ERROR_CODE);
    }
  }

  private static class ExceptionWithTwoStringsConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC_TWICE";

    public ExceptionWithTwoStringsConstructorException(String useless, String anotherUseless) {
      super(ERROR_CODE);
    }
  }

  private static class ExceptionWithThrowableConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC_ESCAPE!";

    public ExceptionWithThrowableConstructorException(Throwable e) {
      super(ERROR_CODE);
    }
  }

  private static class ExceptionWithStringAndThrowableConstructorException
      extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "PRISON_BREAK!";

    public ExceptionWithStringAndThrowableConstructorException(String string, Throwable e) {
      super(ERROR_CODE, string, e);
    }
  }

  private static class ExceptionWithInvalidConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "INVALID_INPUT";

    public ExceptionWithInvalidConstructorException(
        Integer uselessInt, Throwable e, String useless) {
      super(ERROR_CODE, e);
    }
  }

  private abstract static class AbstractServiceException extends ServiceException {
    private static final long serialVersionUID = 1L;

    protected AbstractServiceException(String errorCode) {
      super(errorCode);
    }

    protected AbstractServiceException(String errorCode, String message) {
      super(errorCode, message);
    }
  }

  private static class ConcreteServiceException extends AbstractServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "BLUE_SCREEN_OF_DEATH";

    public ConcreteServiceException(String message) {
      super(ERROR_CODE, message);
    }
  }

  private abstract static class AbstractSubServiceException extends AbstractServiceException {
    private static final long serialVersionUID = 1L;

    protected AbstractSubServiceException(String errorCode) {
      super(errorCode);
    }
  }

  private static class ConcreteSubServiceException extends AbstractSubServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "DEEPER_AND_DEEPER";

    public ConcreteSubServiceException() {
      super(ERROR_CODE);
    }
  }

  private static class DuplicateErrorCodeServiceException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "DEEPER_AND_DEEPER";

    public DuplicateErrorCodeServiceException() {
      super(ERROR_CODE);
    }
  }

  private static class NoErrorCodeServiceException extends ServiceException {
    private static final long serialVersionUID = 1L;

    public NoErrorCodeServiceException() {
      super("");
    }
  }
}
