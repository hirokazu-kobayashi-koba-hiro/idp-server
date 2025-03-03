package org.idp.server.subdomain.webauthn;

import org.idp.server.domain.model.base.BadRequestException;

public class WebAuthnBadRequestException extends BadRequestException {

  public WebAuthnBadRequestException(String message) {
    super(message);
  }

  public WebAuthnBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
