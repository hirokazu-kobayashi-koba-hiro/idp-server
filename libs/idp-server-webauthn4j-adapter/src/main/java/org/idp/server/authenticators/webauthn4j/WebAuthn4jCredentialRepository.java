/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authenticators.webauthn4j;

public interface WebAuthn4jCredentialRepository {
  void register(WebAuthn4jCredential credential);

  WebAuthn4jCredentials findAll(String userId);

  void updateSignCount(String credentialId, long signCount);

  void delete(String credentialId);
}
