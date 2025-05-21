package org.idp.server.core.authentication.exception;

import org.idp.server.platform.exception.NotFoundException;

public class MfaTransactionNotFoundException extends NotFoundException {

  public MfaTransactionNotFoundException(String message) {
    super(message);
  }
}
