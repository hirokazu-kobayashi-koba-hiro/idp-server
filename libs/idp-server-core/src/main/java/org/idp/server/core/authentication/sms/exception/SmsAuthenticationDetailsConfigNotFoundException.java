package org.idp.server.core.authentication.sms.exception;

import org.idp.server.basic.exception.NotFoundException;

public class SmsAuthenticationDetailsConfigNotFoundException extends NotFoundException {
  public SmsAuthenticationDetailsConfigNotFoundException(String message) {
    super(message);
  }
}
