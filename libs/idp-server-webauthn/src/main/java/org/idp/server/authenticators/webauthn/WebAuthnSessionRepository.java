package org.idp.server.authenticators.webauthn;

public interface WebAuthnSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
