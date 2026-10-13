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

package org.idp.server.authentication.interactors.fidouaf;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FidoUafAuthenticationInteractorTest {

  static final String CHALLENGE_DEVICE = "challenge-device";
  static final String OTHER_DEVICE = "other-device";

  List<String> lookedUpDeviceIds = new ArrayList<>();

  static final String MATCH_POST_HOOK =
      """
      "post_hook": {
        "verifications": [
          {
            "type": "match",
            "details": {
              "rules": [
                {
                  "path": "$.execution_http_request.response_body.user_id",
                  "operation": "eq",
                  "target_path": "$.interaction.device_id"
                }
              ]
            }
          }
        ]
      },
      """;

  @Test
  @DisplayName("succeeds when the FIDO server authenticated the device of the challenge")
  void succeedsWithChallengeDevice() {
    AuthenticationInteractionRequestResult result =
        interact(MATCH_POST_HOOK, Map.of("user_id", CHALLENGE_DEVICE));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("user-1", result.user().sub());
  }

  @Test
  @DisplayName("rejects when the FIDO server authenticated another device")
  void rejectsAnotherDevice() {
    AuthenticationInteractionRequestResult result =
        interact(MATCH_POST_HOOK, Map.of("user_id", OTHER_DEVICE));

    assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
    assertEquals("invalid_request", result.response().get("error"));
    assertTrue(lookedUpDeviceIds.isEmpty());
    // No user on the result, so the rejection does not count toward lock conditions
    assertFalse(result.hasUser());
  }

  @Test
  @DisplayName("rejects when the FIDO server response lacks the configured path")
  void rejectsMissingPath() {
    AuthenticationInteractionRequestResult result =
        interact(MATCH_POST_HOOK, Map.of("status", "ok"));

    assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
    assertTrue(lookedUpDeviceIds.isEmpty());
  }

  @Test
  @DisplayName("rejects when the verification type is unknown")
  void rejectsUnknownType() {
    String postHook =
        """
        "post_hook": { "verifications": [ { "type": "matches", "details": {} } ] },
        """;

    AuthenticationInteractionRequestResult result =
        interact(postHook, Map.of("user_id", CHALLENGE_DEVICE));

    assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
  }

  @Test
  @DisplayName("keeps the current behavior without post_hook")
  void succeedsWithoutPostHook() {
    AuthenticationInteractionRequestResult result = interact("", Map.of("status", "ok"));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(List.of(CHALLENGE_DEVICE), lookedUpDeviceIds);
  }

  private AuthenticationInteractionRequestResult interact(
      String postHook, Map<String, Object> fidoResponseBody) {
    StaticExecutor executor =
        new StaticExecutor(
            Map.of("execution_http_request", Map.of("response_body", fidoResponseBody)));
    FidoUafAuthenticationInteractor interactor =
        new FidoUafAuthenticationInteractor(
            new AuthenticationExecutors(Map.of(executor.function(), executor)),
            new ConfigurationRepository(postHook),
            new InteractionRepository(),
            new FidoUafAdditionalRequestResolvers(List.of()));

    return interactor.interact(
        tenant(),
        transaction(),
        StandardAuthenticationInteraction.FIDO_UAF_AUTHENTICATION.toType(),
        new AuthenticationInteractionRequest(new HashMap<>()),
        new RequestAttributes(),
        userQueryRepository());
  }

  /** Returns the user who owns the challenge device, and records the looked-up device IDs. */
  private UserQueryRepository userQueryRepository() {
    return (UserQueryRepository)
        Proxy.newProxyInstance(
            UserQueryRepository.class.getClassLoader(),
            new Class<?>[] {UserQueryRepository.class},
            (proxy, method, args) -> {
              if (!method.getName().equals("findByAuthenticationDevice")) {
                throw new UnsupportedOperationException(method.getName());
              }
              String deviceId = (String) args[1];
              lookedUpDeviceIds.add(deviceId);
              if (!CHALLENGE_DEVICE.equals(deviceId)) {
                return new User();
              }
              User user = new User().setSub("user-1");
              user.addAuthenticationDevice(
                  new AuthenticationDevice(
                      CHALLENGE_DEVICE, null, null, null, null, null, null, null, null, 1));
              return user;
            });
  }

  private static AuthenticationTransaction transaction() {
    AuthenticationRequest request =
        new AuthenticationRequest(
            StandardAuthFlow.OAUTH.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            new User(),
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        new AuthenticationPolicy(),
        new AuthenticationTransactionAttributes());
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

  static class StaticExecutor implements AuthenticationExecutor {

    Map<String, Object> response;

    StaticExecutor(Map<String, Object> response) {
      this.response = response;
    }

    @Override
    public String function() {
      return "static";
    }

    @Override
    public AuthenticationExecutionResult execute(
        Tenant tenant,
        AuthenticationTransactionIdentifier identifier,
        AuthenticationExecutionRequest request,
        RequestAttributes requestAttributes,
        AuthenticationExecutionConfig configuration) {
      return AuthenticationExecutionResult.success(response);
    }
  }

  static class InteractionRepository implements AuthenticationInteractionQueryRepository {

    @Override
    public <T> T get(
        Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz) {
      return clazz.cast(new FidoUafInteraction(CHALLENGE_DEVICE));
    }

    @Override
    public long findTotalCount(Tenant tenant, AuthenticationInteractionQueries queries) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AuthenticationInteraction> findList(
        Tenant tenant, AuthenticationInteractionQueries queries) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AuthenticationInteraction find(
        Tenant tenant, AuthenticationTransactionIdentifier identifier, String key) {
      return new AuthenticationInteraction(
          identifier.value(), key, Map.of("device_id", CHALLENGE_DEVICE));
    }
  }

  static class ConfigurationRepository implements AuthenticationConfigurationQueryRepository {

    String postHook;

    ConfigurationRepository(String postHook) {
      this.postHook = postHook;
    }

    @Override
    public AuthenticationConfiguration get(Tenant tenant, String key) {
      String json =
          """
          {
            "id": "5ee62e7d-e1f0-4f38-b714-0c5d6a28d8f1",
            "type": "fido-uaf",
            "interactions": {
              "fido-uaf-authentication": {
                "execution": {
                  "function": "static",
                  "previous_interaction": { "key": "fido-uaf-authentication-challenge" }
                },
                %s
                "response": { "body_mapping_rules": [ { "from": "$", "to": "*" } ] }
              }
            }
          }
          """
              .formatted(postHook);
      return JsonConverter.snakeCaseInstance().read(json, AuthenticationConfiguration.class);
    }

    @Override
    public AuthenticationConfiguration find(Tenant tenant, String key) {
      return get(tenant, key);
    }

    @Override
    public AuthenticationConfiguration find(
        Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AuthenticationConfiguration findWithDisabled(
        Tenant tenant, AuthenticationConfigurationIdentifier identifier, boolean includeDisabled) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long findTotalCount(Tenant tenant) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset) {
      throw new UnsupportedOperationException();
    }
  }
}
