package com.coveo.feign;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import feign.RequestLine;

public class ReflectionErrorDecoderTestClasses {
  public interface TestApiWithExceptionsWithMultipleFields {
    @RequestLine(value = "")
    void soManyFields() throws MultipleFieldsException;
  }

  public interface TestApiWithExceptionsWithMultipleConstructors {
    @RequestLine(value = "")
    void soManyConstructors() throws MultipleConstructorsException;
  }

  public interface TestApiWithExceptionsWithMultipleConstructorsWithOnlyThrowables {
    @RequestLine(value = "")
    void soManyConstructorsWithThrowables()
        throws MultipleConstructorsWithOnlyThrowableArgumentsException;
  }

  public interface TestApiWithExceptionsNotExtendingServiceException {
    @RequestLine(value = "")
    void methodWithEmptyConstructorException() throws Exception;
  }

  public interface TestApiWithExceptionHardcodingDetailMessage {
    @RequestLine(value = "")
    void methodHardcodedDetailMessageException() throws ExceptionHardcodingDetailMessage;
  }

  public interface TestApiWithExceptionsWithInvalidConstructor {
    @RequestLine(value = "")
    void methodWithInvalidConstructor()
        throws ConcreteSubServiceException, ExceptionWithInvalidConstructorException;
  }

  public interface TestApiWithMethodsNotAnnotated {
    @RequestLine(value = "")
    void methodWithEmptyConstructorException() throws ExceptionWithEmptyConstructorException;

    void methodNotAnnotated() throws ConcreteSubServiceException;
  }

  public interface TestApiClassWithPlainExceptions {
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

    @GetMapping("")
    void methodWithGetMappingAndExceptionConstructorException()
        throws ExceptionWithExceptionConstructorException;
  }

  public interface TestApiClassWithSpringAnnotations {
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

  public interface TestApiClassWithInheritedExceptions {
    @RequestLine("")
    void methodWithAbstractException() throws AbstractServiceException;
  }

  public interface TestApiClassWithDuplicateErrorCodeException {
    @RequestLine("")
    void methodWithDuplicateErrorCodeException()
        throws ConcreteSubServiceException, DuplicateErrorCodeServiceException;
  }

  public interface TestApiClassWithNoErrorCodeServiceException {
    @RequestLine("")
    void methodWithEmptyErrorCodeException() throws NoErrorCodeServiceException;
  }

  public static class ExceptionHardcodingDetailMessage extends ServiceException {
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

  public static class ExceptionWithEmptyConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "BSOD";

    public ExceptionWithEmptyConstructorException() {
      super(ERROR_CODE);
    }
  }

  public static class ExceptionWithStringConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC";

    @SuppressWarnings("unused")
    public ExceptionWithStringConstructorException(String useless) {
      super(ERROR_CODE);
    }
  }

  public static class ExceptionWithTwoStringsConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC_TWICE";

    @SuppressWarnings("unused")
    public ExceptionWithTwoStringsConstructorException(String useless, String anotherUseless) {
      super(ERROR_CODE);
    }
  }

  public static class ExceptionWithThrowableConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "KERNEL_PANIC_ESCAPE!";

    @SuppressWarnings("unused")
    public ExceptionWithThrowableConstructorException(Throwable e) {
      super(ERROR_CODE);
    }
  }

  public static class ExceptionWithExceptionConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "OOPS!";

    public ExceptionWithExceptionConstructorException(Exception e) {
      super(ERROR_CODE, e);
    }
  }

  public static class ExceptionWithStringAndThrowableConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "PRISON_BREAK!";

    public ExceptionWithStringAndThrowableConstructorException(String string, Throwable e) {
      super(ERROR_CODE, string, e);
    }
  }

  public static class ExceptionWithInvalidConstructorException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "INVALID_INPUT";

    @SuppressWarnings("unused")
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

  public static class ConcreteServiceException extends AbstractServiceException {
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

  public static class ConcreteSubServiceException extends AbstractSubServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "DEEPER_AND_DEEPER";

    public ConcreteSubServiceException() {
      super(ERROR_CODE);
    }
  }

  public static class DuplicateErrorCodeServiceException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "DEEPER_AND_DEEPER";

    public DuplicateErrorCodeServiceException() {
      super(ERROR_CODE);
    }
  }

  public static class NoErrorCodeServiceException extends ServiceException {
    private static final long serialVersionUID = 1L;

    public NoErrorCodeServiceException() {
      super("");
    }
  }

  public static class MultipleConstructorsException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "I'M SEEING DOUBLE";

    public MultipleConstructorsException(String message, Throwable cause) {
      super(ERROR_CODE, message, cause);
    }

    public MultipleConstructorsException(String message) {
      super(ERROR_CODE, message);
    }

    public MultipleConstructorsException() {
      super(ERROR_CODE);
    }
  }

  public static class MultipleConstructorsWithOnlyThrowableArgumentsException
      extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "THROWABLES, THROWABLES EVERYWHERE";

    public MultipleConstructorsWithOnlyThrowableArgumentsException(
        String message, Throwable cause) {
      super(ERROR_CODE, message, cause);
    }

    public MultipleConstructorsWithOnlyThrowableArgumentsException(Throwable cause) {
      super(ERROR_CODE, "", cause);
    }
  }

  public static class MultipleFieldsException extends ServiceException {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_CODE = "MANY_FIELDS";

    private Integer field1;
    private Double field2;

    private String field3;
    private List<String> field4;
    private Map<String, Object> field5;

    public MultipleFieldsException() {
      super(ERROR_CODE);
    };

    Integer getField1() {
      return field1;
    }

    Double getField2() {
      return field2;
    }

    String getField3() {
      return field3;
    }

    List<String> getField4() {
      return field4;
    }

    Map<String, Object> getField5() {
      return field5;
    }

    public void setField1(Integer field1) {
      this.field1 = field1;
    }

    public void setField2(Double field2) {
      this.field2 = field2;
    }

    public void setField3(String field3) {
      this.field3 = field3;
    }

    public void setField4(List<String> field4) {
      this.field4 = field4;
    }

    public void setField5(Map<String, Object> field5) {
      this.field5 = field5;
    }
  }
}
