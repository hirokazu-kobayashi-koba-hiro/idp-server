package org.idp.server.core.extension.identity.verification.exception;

import org.idp.server.platform.exception.NotFoundException;

public class IdentityVerificationApplicationNotFoundException extends NotFoundException {
  public IdentityVerificationApplicationNotFoundException(String message) {
    super(message);
  }
}
