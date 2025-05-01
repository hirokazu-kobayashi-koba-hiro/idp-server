package org.idp.server.core.identity.verification.exception;

import org.idp.server.basic.exception.NotFoundException;

public class IdentityVerificationApplicationNotFoundException extends NotFoundException {
  public IdentityVerificationApplicationNotFoundException(String message) {
    super(message);
  }
}
