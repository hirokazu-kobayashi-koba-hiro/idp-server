package org.idp.server.authenticators.webauthn.service.internal;

import org.idp.server.authenticators.webauthn.WebAuthnCredential;
import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.authenticators.webauthn.WebAuthnCredentials;
public class WebAuthnCredentialService {

  WebAuthnCredentialRepository webAuthnCredentialRepository;

  public WebAuthnCredentialService(WebAuthnCredentialRepository webAuthnCredentialRepository) {
    this.webAuthnCredentialRepository = webAuthnCredentialRepository;
  }

  public void register(WebAuthnCredential webAuthnCredential) {
    webAuthnCredentialRepository.register(webAuthnCredential);
  }

  public WebAuthnCredentials findAll(String userId) {
    return webAuthnCredentialRepository.findAll(userId);
  }
}
