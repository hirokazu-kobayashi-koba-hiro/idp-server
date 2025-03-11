package org.idp.server.adapters.springboot.subdomain.webauthn;

public interface WebAuthnCredentialRepository {
  void register(WebAuthnCredential credential);

  WebAuthnCredential get(String credentialId);

  WebAuthnCredentials findAll(String userId);

  void updateSignCount(String credentialId, long signCount);

  void delete(String credentialId);
}
