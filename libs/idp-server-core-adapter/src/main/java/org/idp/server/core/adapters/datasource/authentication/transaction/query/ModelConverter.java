package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthorizationIdentifier identifier = new AuthorizationIdentifier(map.get("authorization_id"));
    AuthorizationFlow authorizationFlow = AuthorizationFlow.of(map.get("authorization_flow"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    User user = toUser(map);
    AuthenticationContext context = toAuthenticationContext(map);
    AuthenticationPolicy authenticationPolicy =
        jsonConverter.read(map.get("authentication_policy"), AuthenticationPolicy.class);
    LocalDateTime createdAt = LocalDateTime.parse(map.get("created_at"));
    LocalDateTime expiredAt = LocalDateTime.parse(map.get("expired_at"));
    AuthenticationRequest request =
        new AuthenticationRequest(
            authorizationFlow,
            tenantIdentifier,
            requestedClientId,
            user,
            context,
            createdAt,
            expiredAt);

    AuthenticationInteractionResults interactionResults = toAuthenticationInteractionResults(map);
    return new AuthenticationTransaction(
        identifier, request, authenticationPolicy, interactionResults);
  }

  private static AuthenticationContext toAuthenticationContext(Map<String, String> map) {
    if (map.containsKey("context") && map.get("context") != null) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(map.get("context"));
      String acrValues = jsonNodeWrapper.getValueOrEmptyAsString("acr_values");
      String scopes = jsonNodeWrapper.getValueOrEmptyAsString("scopes");
      return new AuthenticationContext(acrValues, scopes);
    }

    return new AuthenticationContext();
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
        int callCount = node.getValueAsInt("call_count");
        int successCount = node.getValueAsInt("success_count");
        int failureCount = node.getValueAsInt("failure_count");
        AuthenticationInteractionResult authenticationInteractionResult =
            new AuthenticationInteractionResult(callCount, successCount, failureCount);
        results.put(interaction, authenticationInteractionResult);
      }

      return new AuthenticationInteractionResults(results);
    }

    return new AuthenticationInteractionResults();
  }
}
