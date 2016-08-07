package com.coveo.feign;

public abstract class ServiceException extends Exception {
	private static final long serialVersionUID = 4116691862956368612L;
	private String errorCode;

	protected ServiceException(String errorCode) {
		this.errorCode = errorCode;
	}

	protected ServiceException(String errorCode, Throwable e) {
		super(e);
		this.errorCode = errorCode;
	}

	protected ServiceException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	protected ServiceException(String errorCode, String message, Throwable innerException) {
		super(message, innerException);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}
}
