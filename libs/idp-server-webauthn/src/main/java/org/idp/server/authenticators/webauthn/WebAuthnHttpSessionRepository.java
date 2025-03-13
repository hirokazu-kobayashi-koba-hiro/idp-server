package org.idp.server.authenticators.webauthn;

public interface WebAuthnHttpSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
