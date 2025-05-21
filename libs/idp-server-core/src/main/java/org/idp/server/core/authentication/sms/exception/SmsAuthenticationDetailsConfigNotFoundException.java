package org.idp.server.core.authentication.sms.exception;

import org.idp.server.platform.exception.NotFoundException;

public class SmsAuthenticationDetailsConfigNotFoundException extends NotFoundException {
  public SmsAuthenticationDetailsConfigNotFoundException(String message) {
    super(message);
  }
}
