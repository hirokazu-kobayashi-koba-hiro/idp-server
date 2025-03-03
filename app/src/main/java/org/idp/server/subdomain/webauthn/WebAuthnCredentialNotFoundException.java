package org.idp.server.subdomain.webauthn;

import org.idp.server.domain.model.base.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
