package org.idp.server.core.authentication.sms.exception;

import org.idp.server.platform.exception.NotFoundException;

public class SmsAuthenticationExecutionConfigNotFoundException extends NotFoundException {
  public SmsAuthenticationExecutionConfigNotFoundException(String message) {
    super(message);
  }
}
