package org.idp.server.core.mfa.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class MfaTransactionNotFoundException extends NotFoundException {

  public MfaTransactionNotFoundException(String message) {
    super(message);
  }
}
