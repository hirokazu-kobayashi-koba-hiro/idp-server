package org.idp.server.adapters.springboot.subdomain.webauthn;

public interface WebAuthnSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
