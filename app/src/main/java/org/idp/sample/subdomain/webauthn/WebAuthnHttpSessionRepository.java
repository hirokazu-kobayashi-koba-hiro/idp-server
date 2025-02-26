package org.idp.sample.subdomain.webauthn;

public interface WebAuthnHttpSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
