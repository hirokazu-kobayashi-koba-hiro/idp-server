package org.idp.server.subdomain.webauthn;

public interface WebAuthnHttpSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
