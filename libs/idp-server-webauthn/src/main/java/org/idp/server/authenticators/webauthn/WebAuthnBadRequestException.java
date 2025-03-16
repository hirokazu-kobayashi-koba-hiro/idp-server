package org.idp.server.authenticators.webauthn;

import org.idp.server.core.type.exception.BadRequestException;

public class WebAuthnBadRequestException extends BadRequestException {

  public WebAuthnBadRequestException(String message) {
    super(message);
  }

  public WebAuthnBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
