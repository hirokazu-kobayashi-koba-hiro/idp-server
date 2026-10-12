/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authenticators.webauthn4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebAuthn4jDeregistrationExecutorTest {

  static final String CREDENTIAL_ID = "credential-1";
  static final String OWNER = "owner@example.com";

  @Test
  @DisplayName("deletes the credential when it belongs to the user")
  void deletesOwnCredential() {
    InMemoryCredentialRepository repository = new InMemoryCredentialRepository();
    repository.store(credential(CREDENTIAL_ID, OWNER));

    AuthenticationExecutionResult result =
        execute(repository, Map.of("credential_id", CREDENTIAL_ID, "username", OWNER));

    assertTrue(result.isSuccess());
    assertEquals(List.of(CREDENTIAL_ID), repository.deleted);
  }

  @Test
  @DisplayName("does not delete another user's credential and reports it as not found")
  void rejectsOtherUsersCredential() {
    InMemoryCredentialRepository repository = new InMemoryCredentialRepository();
    repository.store(credential(CREDENTIAL_ID, OWNER));

    AuthenticationExecutionResult result =
        execute(
            repository, Map.of("credential_id", CREDENTIAL_ID, "username", "other@example.com"));

    assertTrue(result.isClientError());
    assertEquals("credential_not_found", error(result));
    assertTrue(repository.deleted.isEmpty());
  }

  @Test
  @DisplayName("does not delete when the owner username is missing")
  void rejectsWithoutUsername() {
    InMemoryCredentialRepository repository = new InMemoryCredentialRepository();
    repository.store(credential(CREDENTIAL_ID, OWNER));

    AuthenticationExecutionResult result =
        execute(repository, Map.of("credential_id", CREDENTIAL_ID));

    assertTrue(result.isClientError());
    assertEquals("invalid_request", error(result));
    assertTrue(repository.deleted.isEmpty());
  }

  @Test
  @DisplayName("reports a missing credential as not found")
  void rejectsMissingCredential() {
    InMemoryCredentialRepository repository = new InMemoryCredentialRepository();

    AuthenticationExecutionResult result =
        execute(repository, Map.of("credential_id", "missing", "username", OWNER));

    assertTrue(result.isClientError());
    assertEquals("credential_not_found", error(result));
    assertTrue(repository.deleted.isEmpty());
  }

  private static AuthenticationExecutionResult execute(
      InMemoryCredentialRepository repository, Map<String, Object> values) {
    WebAuthn4jDeregistrationExecutor executor = new WebAuthn4jDeregistrationExecutor(repository);
    return executor.execute(
        tenant(),
        new AuthenticationTransactionIdentifier("transaction-1"),
        new AuthenticationExecutionRequest(new HashMap<>(values)),
        null,
        null);
  }

  private static String error(AuthenticationExecutionResult result) {
    return (String) result.contents().get("error");
  }

  private static WebAuthn4jCredential credential(String id, String username) {
    return new WebAuthn4jCredential(
        id,
        "user-handle",
        username,
        username,
        "example.com",
        null,
        "data",
        -7,
        0L,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static Tenant tenant() {
    return new Tenant(
        new TenantIdentifier("2a1b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  static class InMemoryCredentialRepository implements WebAuthn4jCredentialRepository {

    Map<String, WebAuthn4jCredential> credentials = new HashMap<>();
    List<String> deleted = new ArrayList<>();

    void store(WebAuthn4jCredential credential) {
      credentials.put(credential.id(), credential);
    }

    @Override
    public void register(Tenant tenant, WebAuthn4jCredential credential) {
      store(credential);
    }

    @Override
    public WebAuthn4jCredentials findAll(Tenant tenant, String userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WebAuthn4jCredentials findByUsername(Tenant tenant, String username) {
      throw new UnsupportedOperationException();
    }

    @Override
    public WebAuthn4jCredential get(Tenant tenant, String id) {
      WebAuthn4jCredential credential = credentials.get(id);
      if (credential == null) {
        throw new NotFoundException("credential not found");
      }
      return credential;
    }

    @Override
    public void updateSignCount(Tenant tenant, String credentialId, long signCount) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Tenant tenant, String credentialId) {
      credentials.remove(credentialId);
      deleted.add(credentialId);
    }
  }
}
