package org.idp.server.adapters.springboot.subdomain.webauthn;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
