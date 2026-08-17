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
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetail;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(map.get("id"));
    AuthFlow authFlow = new AuthFlow(map.get("flow"));
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
        results.put(interaction, toResult(node));
      }

      return new AuthenticationInteractionResults(results);
    }

    return new AuthenticationInteractionResults();
  }

  /**
   * Rebuilds one interaction result, including its per-interaction breakdown (#1771).
   *
   * <p>The breakdown has to be restored here, not only in {@code
   * AuthenticationInteractionResults#fromMap} (which serves OPSession): every request re-reads the
   * transaction through this converter, so dropping it would reset the breakdown between steps of a
   * multi-step flow while the totals kept accumulating.
   *
   * <p>Note the two meanings of "interactions" in this file: the outer one is the DB column holding
   * the whole result map, the nested one is the per-interaction breakdown of a single result.
   */
  private static AuthenticationInteractionResult toResult(JsonNodeWrapper node) {
    String operationType = node.getValueOrEmptyAsString("operation_type");
    String method = node.getValueOrEmptyAsString("method");
    int callCount = node.getValueAsInt("call_count");
    int successCount = node.getValueAsInt("success_count");
    int failureCount = node.getValueAsInt("failure_count");
    LocalDateTime interactionTime =
        LocalDateTimeParser.parse(node.getValueOrEmptyAsString("interaction_time"));

    return new AuthenticationInteractionResult(
        operationType,
        method,
        callCount,
        successCount,
        failureCount,
        interactionTime,
        toNestedResults(node));
  }

  /**
   * Rows written before the breakdown existed have no nested node and come back with an empty map.
   */
  private static Map<String, AuthenticationInteractionResult> toNestedResults(
      JsonNodeWrapper node) {
    HashMap<String, AuthenticationInteractionResult> nested = new HashMap<>();
    if (!node.contains("interactions")) {
      return nested;
    }

    JsonNodeWrapper nestedNode = node.getValueAsJsonNode("interactions");
    for (Iterator<String> it = nestedNode.fieldNames(); it.hasNext(); ) {
      String name = it.next();
      nested.put(name, toResult(nestedNode.getValueAsJsonNode(name)));
    }
    return nested;
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
      String locale = wrapper.getValueOrEmptyAsString("locale");
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
          locale,
          notificationChannel,
          notificationToken,
          availableAuthenticationMethods,
          priority);
    }

    return new AuthenticationDevice();
  }
}
