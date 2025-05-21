package org.idp.server.authentication.interactors.sms.exception;

import org.idp.server.platform.exception.NotFoundException;

public class SmsAuthenticationDetailsConfigNotFoundException extends NotFoundException {
  public SmsAuthenticationDetailsConfigNotFoundException(String message) {
    super(message);
  }
}
