package org.idp.server.authenticators.webauthn.service.internal;

import org.idp.server.authenticators.webauthn.WebAuthnChallenge;
import org.idp.server.authenticators.webauthn.WebAuthnSession;
import org.idp.server.authenticators.webauthn.WebAuthnSessionRepository;

public class WebAuthnSessionService {

  WebAuthnSessionRepository webAuthnSessionRepository;

  public WebAuthnSessionService(WebAuthnSessionRepository webAuthnSessionRepository) {
    this.webAuthnSessionRepository = webAuthnSessionRepository;
  }

  public WebAuthnSession start() {

    WebAuthnChallenge webAuthnChallenge = WebAuthnChallenge.generate();
    WebAuthnSession webAuthnSession = new WebAuthnSession(webAuthnChallenge);

    webAuthnSessionRepository.register(webAuthnSession);

    return webAuthnSession;
  }

  public WebAuthnSession get() {

    return webAuthnSessionRepository.get();
  }
}
