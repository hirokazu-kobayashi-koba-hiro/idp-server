package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.platform.exception.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
