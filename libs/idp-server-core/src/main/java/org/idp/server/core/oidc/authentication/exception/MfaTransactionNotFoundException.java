package org.idp.server.core.oidc.authentication.exception;

import org.idp.server.platform.exception.NotFoundException;

public class MfaTransactionNotFoundException extends NotFoundException {

  public MfaTransactionNotFoundException(String message) {
    super(message);
  }
}
