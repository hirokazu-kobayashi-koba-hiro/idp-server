package org.idp.server.authenticators.webauthn;

import org.idp.server.core.type.exception.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
