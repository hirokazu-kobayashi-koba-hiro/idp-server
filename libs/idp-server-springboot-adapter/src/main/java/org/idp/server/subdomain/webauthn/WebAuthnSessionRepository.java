package org.idp.server.subdomain.webauthn;

public interface WebAuthnSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
