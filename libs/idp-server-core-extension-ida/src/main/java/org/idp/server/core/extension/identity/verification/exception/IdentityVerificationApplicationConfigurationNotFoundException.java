package org.idp.server.core.extension.identity.verification.exception;

import org.idp.server.platform.exception.NotFoundException;

public class IdentityVerificationApplicationConfigurationNotFoundException
    extends NotFoundException {
  public IdentityVerificationApplicationConfigurationNotFoundException(String message) {
    super(message);
  }
}
