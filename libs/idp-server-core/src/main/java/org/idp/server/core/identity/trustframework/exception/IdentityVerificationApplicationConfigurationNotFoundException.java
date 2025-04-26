package org.idp.server.core.identity.trustframework.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class IdentityVerificationApplicationConfigurationNotFoundException
    extends NotFoundException {
  public IdentityVerificationApplicationConfigurationNotFoundException(String message) {
    super(message);
  }
}
