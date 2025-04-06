package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.type.exception.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
