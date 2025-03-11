package org.idp.server.adapters.springboot.subdomain.webauthn;

public interface WebAuthnHttpSessionRepository {

  void register(WebAuthnSession webAuthnSession);

  WebAuthnSession get();
}
