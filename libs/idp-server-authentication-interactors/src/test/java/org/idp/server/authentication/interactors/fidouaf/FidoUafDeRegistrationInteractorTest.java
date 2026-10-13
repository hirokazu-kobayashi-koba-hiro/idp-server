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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolver;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FidoUafDeRegistrationInteractorTest {

  static final String OWN_DEVICE = "own-device";
  static final String OTHER_DEVICE = "other-device";

  RecordingExecutor executor = new RecordingExecutor();

  @Test
  @DisplayName("sends the user's own device_id to the FIDO server and removes the device")
  void deletesOwnDevice() {
    AuthenticationInteractionRequestResult result =
        interact(List.of(), Map.of("device_id", OWN_DEVICE));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(1, executor.requests.size());
    assertEquals(OWN_DEVICE, executor.requests.get(0).getValueAsString("device_id"));
    assertFalse(result.user().hasAuthenticationDevice(OWN_DEVICE));
  }

  @Test
  @DisplayName("rejects another user's device_id without calling the FIDO server")
  void rejectsDeviceNotOwned() {
    AuthenticationInteractionRequestResult result =
        interact(List.of(), Map.of("device_id", OTHER_DEVICE));

    assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
    assertEquals("invalid_request", result.response().get("error"));
    assertTrue(executor.requests.isEmpty());
    // No user on the result, so the rejection does not count toward lock conditions
    assertFalse(result.hasUser());
  }

  @Test
  @DisplayName("rejects a request without device_id without calling the FIDO server")
  void rejectsWithoutDeviceId() {
    AuthenticationInteractionRequestResult result = interact(List.of(), Map.of());

    assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
    assertEquals("invalid_request", result.response().get("error"));
    assertTrue(executor.requests.isEmpty());
  }

  @Test
  @DisplayName("sends the verified device_id even when a resolver sets another one")
  void overwritesDeviceIdFromResolvers() {
    FidoUafAdditionalRequestResolver resolver = new DeviceIdResolver(OTHER_DEVICE);

    AuthenticationInteractionRequestResult result =
        interact(List.of(resolver), Map.of("device_id", OWN_DEVICE));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(OWN_DEVICE, executor.requests.get(0).getValueAsString("device_id"));
  }

  private AuthenticationInteractionRequestResult interact(
      List<FidoUafAdditionalRequestResolver> resolvers, Map<String, Object> body) {
    FidoUafDeRegistrationInteractor interactor =
        new FidoUafDeRegistrationInteractor(
            new AuthenticationExecutors(Map.of(executor.function(), executor)),
            new ConfigurationRepository(),
            new FidoUafAdditionalRequestResolvers(resolvers));

    return interactor.interact(
        tenant(),
        transaction(),
        StandardAuthenticationInteraction.FIDO_UAF_DEREGISTRATION.toType(),
        new AuthenticationInteractionRequest(new HashMap<>(body)),
        null,
        null);
  }

  private static AuthenticationTransaction transaction() {
    User user = new User().setSub("user-1");
    user.addAuthenticationDevice(
        new AuthenticationDevice(OWN_DEVICE, null, null, null, null, null, null, null, null, 1));
    AuthenticationRequest request =
        new AuthenticationRequest(
            StandardAuthFlow.FIDO_UAF_DEREGISTRATION.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            user,
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

  static class RecordingExecutor implements AuthenticationExecutor {

    List<AuthenticationExecutionRequest> requests = new ArrayList<>();

    @Override
    public String function() {
      return "recording";
    }

    @Override
    public AuthenticationExecutionResult execute(
        Tenant tenant,
        AuthenticationTransactionIdentifier identifier,
        AuthenticationExecutionRequest request,
        RequestAttributes requestAttributes,
        AuthenticationExecutionConfig configuration) {
      requests.add(request);
      return AuthenticationExecutionResult.success(Map.of("deleted", true));
    }
  }

  static class DeviceIdResolver implements FidoUafAdditionalRequestResolver {

    String deviceId;

    DeviceIdResolver(String deviceId) {
      this.deviceId = deviceId;
    }

    @Override
    public boolean shouldResolve(
        Tenant tenant,
        AuthenticationInteractionType type,
        AuthenticationInteractionRequest request,
        AuthenticationTransaction transaction) {
      return true;
    }

    @Override
    public Map<String, Object> resolve(
        Tenant tenant,
        AuthenticationInteractionType type,
        AuthenticationInteractionRequest request,
        AuthenticationTransaction transaction) {
      return Map.of("device_id", deviceId);
    }
  }

  static class ConfigurationRepository implements AuthenticationConfigurationQueryRepository {

    @Override
    public AuthenticationConfiguration get(Tenant tenant, String key) {
      String json =
          """
          {
            "id": "5ee62e7d-e1f0-4f38-b714-0c5d6a28d8f1",
            "type": "fido-uaf",
            "interactions": {
              "fido-uaf-deregistration": {
                "execution": { "function": "recording" },
                "response": { "body_mapping_rules": [ { "from": "$", "to": "*" } ] }
              }
            }
          }
          """;
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
