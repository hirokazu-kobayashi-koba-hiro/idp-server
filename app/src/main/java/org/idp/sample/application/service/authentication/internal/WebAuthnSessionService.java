package org.idp.sample.application.service.authentication.internal;

import org.idp.sample.subdomain.webauthn.WebAuthnChallenge;
import org.idp.sample.subdomain.webauthn.WebAuthnSession;
import org.idp.sample.subdomain.webauthn.WebAuthnSessionRepository;
import org.springframework.stereotype.Service;

@Service
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
