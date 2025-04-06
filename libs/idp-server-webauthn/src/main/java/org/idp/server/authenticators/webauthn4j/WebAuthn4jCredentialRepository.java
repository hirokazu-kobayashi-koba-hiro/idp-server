package org.idp.server.authenticators.webauthn4j;

public interface WebAuthn4jCredentialRepository {
  void register(WebAuthn4jCredential credential);

  WebAuthn4jCredentials findAll(String userId);

  void updateSignCount(String credentialId, long signCount);

  void delete(String credentialId);
}
