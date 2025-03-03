package org.idp.server.application.service.authentication.internal;

import org.idp.server.subdomain.webauthn.WebAuthnCredential;
import org.idp.server.subdomain.webauthn.WebAuthnCredentialRepository;
import org.idp.server.subdomain.webauthn.WebAuthnCredentials;
import org.springframework.stereotype.Service;

@Service
public class WebAuthnCredentialService {

  WebAuthnCredentialRepository webAuthnCredentialRepository;

  public WebAuthnCredentialService(WebAuthnCredentialRepository webAuthnCredentialRepository) {
    this.webAuthnCredentialRepository = webAuthnCredentialRepository;
  }

  public void register(WebAuthnCredential webAuthnCredential) {
    webAuthnCredentialRepository.register(webAuthnCredential);
  }

  public WebAuthnCredential get(String id) {
    return webAuthnCredentialRepository.get(id);
  }

  public WebAuthnCredentials findAll(String userId) {
    return webAuthnCredentialRepository.findAll(userId);
  }
}
