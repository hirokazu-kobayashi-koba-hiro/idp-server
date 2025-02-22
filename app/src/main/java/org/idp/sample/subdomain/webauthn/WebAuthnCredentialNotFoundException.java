package org.idp.sample.subdomain.webauthn;

import org.idp.sample.domain.model.base.NotFoundException;

public class WebAuthnCredentialNotFoundException extends NotFoundException {
  public WebAuthnCredentialNotFoundException(String message) {
    super(message);
  }
}
