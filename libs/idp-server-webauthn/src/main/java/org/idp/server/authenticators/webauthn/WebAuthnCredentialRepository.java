package org.idp.server.authenticators.webauthn;

public interface WebAuthnCredentialRepository {
  void register(WebAuthnCredential credential);

  WebAuthnCredentials findAll(String userId);

  void updateSignCount(String credentialId, long signCount);

  void delete(String credentialId);
}
