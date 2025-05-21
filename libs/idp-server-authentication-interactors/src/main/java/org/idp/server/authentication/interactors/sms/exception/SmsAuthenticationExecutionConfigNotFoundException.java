package org.idp.server.authentication.interactors.sms.exception;

import org.idp.server.platform.exception.NotFoundException;

public class SmsAuthenticationExecutionConfigNotFoundException extends NotFoundException {
  public SmsAuthenticationExecutionConfigNotFoundException(String message) {
    super(message);
  }
}
