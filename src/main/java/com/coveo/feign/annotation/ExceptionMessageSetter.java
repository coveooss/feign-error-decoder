/*
 * Copyright (c) Coveo Solutions Inc.
 */
package com.coveo.feign.annotation;

public interface ExceptionMessageSetter {
  /**
   * Method to set a field that will be returned by {@link Throwable#getMessage()} method.
   *
   * This is necessary not to break encapsulation that is now strictly enforced since JDK 16.
   *
   * @param detailMessage The message to be returned by the {@link Throwable#getMessage()} method.
   */
  void setExceptionMessage(String detailMessage);
}
