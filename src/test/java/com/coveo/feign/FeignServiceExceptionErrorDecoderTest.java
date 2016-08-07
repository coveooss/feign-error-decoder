package com.coveo.feign;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.RequestLine;
import feign.Response;

@RunWith(MockitoJUnitRunner.class)
public class FeignServiceExceptionErrorDecoderTest {
	private static final String DUMMY_MESSAGE = "dummy message";

	@Test
	public void testWithPlainExceptions() throws Exception {
		Map<String, ThrownServiceExceptionDetails> exceptionsThrown = getExceptionsThrownMapFromErrorDecoder(
				TestApiClassWithPlainExceptions.class);

		assertThat(exceptionsThrown.size(), is(5));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithEmptyConstructorException.ERROR_CODE));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithStringConstructorException.ERROR_CODE));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithTwoStringsConstructorException.ERROR_CODE));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithThrowableConstructorException.ERROR_CODE));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithStringAndThrowableConstructorException.ERROR_CODE));
	}

	@Test
	public void testWithInheritedExceptions() throws Exception {
		Map<String, ThrownServiceExceptionDetails> exceptionsThrown = getExceptionsThrownMapFromErrorDecoder(
				TestApiClassWithInheritedExceptions.class);

		assertThat(exceptionsThrown.size(), is(2));
		assertTrue(exceptionsThrown.containsKey(ConcreteServiceException.ERROR_CODE));
		assertTrue(exceptionsThrown.containsKey(ConcreteSubServiceException.ERROR_CODE));
	}

	@Test
	public void testWithUnannotatedMethod() throws Exception {
		Map<String, ThrownServiceExceptionDetails> exceptionsThrown = getExceptionsThrownMapFromErrorDecoder(
				TestApiWithMethodsNotAnnotated.class);

		assertThat(exceptionsThrown.size(), is(1));
		assertTrue(exceptionsThrown.containsKey(ExceptionWithEmptyConstructorException.ERROR_CODE));
	}

	@Test
	public void testApiWithExceptionsNotExtendingServiceException() throws Exception {
		Map<String, ThrownServiceExceptionDetails> exceptionsThrown = getExceptionsThrownMapFromErrorDecoder(
				TestApiWithExceptionsNotExtendingServiceException.class);

		assertThat(exceptionsThrown.size(), is(0));
	}

	@Test
	public void testApiWithExceptionWithInvalidConstructor() throws Exception {
		Map<String, ThrownServiceExceptionDetails> exceptionsThrown = getExceptionsThrownMapFromErrorDecoder(
				TestApiWithExceptionsWithInvalidConstructor.class);

		assertThat(exceptionsThrown.size(), is(1));
		assertTrue(exceptionsThrown.containsKey(ConcreteSubServiceException.ERROR_CODE));
	}

	@Test
	public void testDecodeThrownException() throws Exception {
		FeignServiceExceptionErrorDecoder errorDecoder = new FeignServiceExceptionErrorDecoder(
				TestApiClassWithPlainExceptions.class);
		Response response = getResponseWithRestExceptionWithErrorCode(ExceptionWithEmptyConstructorException.ERROR_CODE,
				DUMMY_MESSAGE);

		Exception exception = errorDecoder.decode("", response);

		assertThat(exception, instanceOf(ExceptionWithEmptyConstructorException.class));
		assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
	}

	@Test
	public void testDecodeThrownExceptionWithHardcodedMessage() throws Exception {
		FeignServiceExceptionErrorDecoder errorDecoder = new FeignServiceExceptionErrorDecoder(
				TestApiWithExceptionHardcodingDetailMessage.class);
		ExceptionHardcodingDetailMessage originalException = new ExceptionHardcodingDetailMessage();

		assertThat(originalException.getMessage(), is(not(DUMMY_MESSAGE)));

		Response response = getResponseWithRestExceptionWithErrorCode(ExceptionHardcodingDetailMessage.ERROR_CODE,
				DUMMY_MESSAGE);

		Exception exception = errorDecoder.decode("", response);

		assertThat(exception, instanceOf(ExceptionHardcodingDetailMessage.class));
		assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
	}

	@Test
	public void testDecodeThrownAbstractException() throws Exception {
		FeignServiceExceptionErrorDecoder errorDecoder = new FeignServiceExceptionErrorDecoder(
				TestApiClassWithInheritedExceptions.class);
		Response response = getResponseWithRestExceptionWithErrorCode(ConcreteServiceException.ERROR_CODE,
				DUMMY_MESSAGE);

		Exception exception = errorDecoder.decode("", response);

		assertThat(exception, instanceOf(ConcreteServiceException.class));
		assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
	}

	@Test
	public void testDecodeThrownSubAbstractException() throws Exception {
		FeignServiceExceptionErrorDecoder errorDecoder = new FeignServiceExceptionErrorDecoder(
				TestApiClassWithInheritedExceptions.class);
		Response response = getResponseWithRestExceptionWithErrorCode(ConcreteSubServiceException.ERROR_CODE,
				DUMMY_MESSAGE);

		Exception exception = errorDecoder.decode("", response);

		assertThat(exception, instanceOf(ConcreteSubServiceException.class));
		assertThat(exception.getMessage(), is(DUMMY_MESSAGE));
	}

	@Test(expected = IllegalStateException.class)
	public void shouldThrowOnDistinctExceptionsWithTheSameErrorCode() throws Exception {
		new FeignServiceExceptionErrorDecoder(TestApiClassWithDuplicateErrorCodeException.class);
	}

	@Test(expected = IllegalStateException.class)
	public void shouldThrowOnExceptionsWithTheNoErrorCode() throws Exception {
		new FeignServiceExceptionErrorDecoder(TestApiClassWithNoErrorCodeServiceException.class);
	}

	private Response getResponseWithRestExceptionWithErrorCode(String errorCode, String message)
			throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		return Response.create(412, "", new HashMap<String, Collection<String>>(),
				objectMapper
						.writeValueAsString(new ErrorCodeAndMessage().withErrorCode(errorCode).withMessage(message)),
				Charset.forName("UTF-8"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, ThrownServiceExceptionDetails> getExceptionsThrownMapFromErrorDecoder(Class<?> apiInterface)
			throws Exception {
		FeignServiceExceptionErrorDecoder errorDecoder = new FeignServiceExceptionErrorDecoder(apiInterface);
		Field exceptionsThrownField = errorDecoder.getClass().getDeclaredField("exceptionsThrown");
		exceptionsThrownField.setAccessible(true);
		return (Map<String, ThrownServiceExceptionDetails>) exceptionsThrownField.get(errorDecoder);
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
		void methodWithTwoStringsConstructorException() throws ExceptionWithTwoStringsConstructorException;

		@RequestLine("")
		void methodWithThrowableConstructorException() throws ExceptionWithThrowableConstructorException;

		@RequestLine("")
		void methodWithStringAndThrowableConstructorException()
				throws ExceptionWithStringAndThrowableConstructorException;

		@RequestLine("")
		void anotherMethodWithStringAndThrowableConstructorException()
				throws ExceptionWithStringAndThrowableConstructorException;
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

	private static class ExceptionWithStringAndThrowableConstructorException extends ServiceException {
		private static final long serialVersionUID = 1L;
		public static final String ERROR_CODE = "PRISON_BREAK!";

		public ExceptionWithStringAndThrowableConstructorException(String string, Throwable e) {
			super(ERROR_CODE, string, e);
		}
	}

	private static class ExceptionWithInvalidConstructorException extends ServiceException {
		private static final long serialVersionUID = 1L;
		public static final String ERROR_CODE = "INVALID_INPUT";

		public ExceptionWithInvalidConstructorException(Throwable e, String useless) {
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
