package org.idp.sample.subdomain.webauthn;

public interface WebAuthnSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
