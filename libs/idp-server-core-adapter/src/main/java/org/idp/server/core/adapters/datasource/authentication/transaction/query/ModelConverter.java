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

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.client.ClientAttributes;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.rar.AuthorizationDetail;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.type.AuthFlow;
import org.idp.server.core.oidc.type.ciba.BindingMessage;
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.type.oauth.Scopes;
import org.idp.server.core.oidc.type.oidc.AcrValues;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(map.get("id"));
    AuthFlow authFlow = AuthFlow.of(map.get("flow"));
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(map.get("authorization_id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    TenantAttributes tenantAttributes = toTenantAttributes(map);
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    ClientAttributes clientAttributes =
        jsonConverter.read(map.get("client_payload"), ClientAttributes.class);
    User user = toUser(map);
    AuthenticationDevice authenticationDevice = toAuthenticationDevice(map);
    AuthenticationContext context = toAuthenticationContext(map);
    AuthenticationPolicy authenticationPolicy =
        jsonConverter.read(map.get("authentication_policy"), AuthenticationPolicy.class);
    LocalDateTime createdAt = LocalDateTimeParser.parse(map.get("created_at"));
    LocalDateTime expiredAt = LocalDateTimeParser.parse(map.get("expires_at"));
    AuthenticationRequest request =
        new AuthenticationRequest(
            authFlow,
            tenantIdentifier,
            tenantAttributes,
            requestedClientId,
            clientAttributes,
            user,
            authenticationDevice,
            context,
            createdAt,
            expiredAt);

    AuthenticationInteractionResults interactionResults = toAuthenticationInteractionResults(map);
    AuthenticationTransactionAttributes attributes = toAuthenticationTransactionAttributes(map);

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        request,
        authenticationPolicy,
        interactionResults,
        attributes);
  }

  private static AuthenticationContext toAuthenticationContext(Map<String, String> map) {
    if (map.containsKey("context") && map.get("context") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("context"));
      String acrValues = jsonNodeWrapper.getValueOrEmptyAsString("acr_values");
      String scopes = jsonNodeWrapper.getValueOrEmptyAsString("scopes");
      String bindingMessage = jsonNodeWrapper.getValueOrEmptyAsString("binding_message");
      JsonNodeWrapper detailsNode = jsonNodeWrapper.getNode("authorization_details");
      List<Map<String, Object>> listAsMap = detailsNode.toListAsMap();
      List<AuthorizationDetail> authorizationDetailsList =
          listAsMap.stream().map(AuthorizationDetail::new).toList();

      return new AuthenticationContext(
          new AcrValues(acrValues),
          new Scopes(scopes),
          new BindingMessage(bindingMessage),
          new AuthorizationDetails(authorizationDetailsList));
    }

    return new AuthenticationContext();
  }

  static TenantAttributes toTenantAttributes(Map<String, String> map) {

    if (map.containsKey("tenant_payload") && map.get("tenant_payload") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("tenant_payload"));
      return new TenantAttributes(jsonNodeWrapper.toMap());
    }

    return new TenantAttributes();
  }

  static User toUser(Map<String, String> map) {
    if (map.containsKey("user_payload") && map.get("user_payload") != null) {
      return jsonConverter.read(map.get("user_payload"), User.class);
    }
    return User.notFound();
  }

  static AuthenticationInteractionResults toAuthenticationInteractionResults(
      Map<String, String> map) {
    if (map.containsKey("interactions") && map.get("interactions") != null) {

      HashMap<String, AuthenticationInteractionResult> results = new HashMap<>();
      JsonNodeWrapper interactions = JsonNodeWrapper.fromString(map.get("interactions"));

      for (Iterator<String> it = interactions.fieldNames(); it.hasNext(); ) {
        String interaction = it.next();
        JsonNodeWrapper node = interactions.getValueAsJsonNode(interaction);
        String operationType = node.getValueOrEmptyAsString("operation_type");
        String method = node.getValueOrEmptyAsString("method");
        int callCount = node.getValueAsInt("call_count");
        int successCount = node.getValueAsInt("success_count");
        int failureCount = node.getValueAsInt("failure_count");
        LocalDateTime interactionTIme =
            LocalDateTimeParser.parse(node.getValueOrEmptyAsString("interaction_time"));
        AuthenticationInteractionResult authenticationInteractionResult =
            new AuthenticationInteractionResult(
                operationType, method, callCount, successCount, failureCount, interactionTIme);
        results.put(interaction, authenticationInteractionResult);
      }

      return new AuthenticationInteractionResults(results);
    }

    return new AuthenticationInteractionResults();
  }

  static AuthenticationTransactionAttributes toAuthenticationTransactionAttributes(
      Map<String, String> map) {
    if (map.containsKey("attributes") && map.get("attributes") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("attributes"));
      Map<String, Object> attributesMap = jsonNodeWrapper.toMap();
      return new AuthenticationTransactionAttributes(attributesMap);
    }

    return new AuthenticationTransactionAttributes();
  }

  static AuthenticationDevice toAuthenticationDevice(Map<String, String> map) {

    if (map.containsKey("authentication_device_payload")
        && map.get("authentication_device_payload") != null) {
      JsonNodeWrapper wrapper =
          JsonNodeWrapper.fromString(map.get("authentication_device_payload"));

      String id = wrapper.getValueOrEmptyAsString("id");
      String appName = wrapper.getValueOrEmptyAsString("app_name");
      String platform = wrapper.getValueOrEmptyAsString("platform");
      String os = wrapper.getValueOrEmptyAsString("os");
      String model = wrapper.getValueOrEmptyAsString("model");
      String notificationChannel = wrapper.getValueOrEmptyAsString("notification_channel");
      String notificationToken = wrapper.getValueOrEmptyAsString("notification_token");
      JsonNodeWrapper availableAuthenticationMethodsNodes = wrapper.getNode("available_methods");
      List<String> availableAuthenticationMethods = availableAuthenticationMethodsNodes.toList();
      Integer priority = wrapper.getValueAsInteger("priority");
      return new AuthenticationDevice(
          id,
          appName,
          platform,
          os,
          model,
          notificationChannel,
          notificationToken,
          availableAuthenticationMethods,
          priority);
    }

    return new AuthenticationDevice();
  }
}
