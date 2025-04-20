package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.AuthenticationInteractionResults;
import org.idp.server.core.authentication.AuthenticationRequest;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.AuthorizationFlow;
import org.idp.server.core.type.oauth.RequestedClientId;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthenticationTransaction convert(Map<String, String> map) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(map.get("authorization_id"));
    AuthorizationFlow authorizationFlow = AuthorizationFlow.valueOf(map.get("authorization_flow"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    User user = toUser(map);
    List<String> availableAuthenticationTypes =
        jsonConverter.read(map.get("available_authentication_types"), List.class);
    List<String> requiredAnyOfAuthenticationTypes = toRequiredAnyOfAuthenticationTypes(map);
    LocalDateTime createdAt = LocalDateTime.parse(map.get("created_at"));
    LocalDateTime expiredAt = LocalDateTime.parse(map.get("expired_at"));
    AuthenticationRequest request =
        new AuthenticationRequest(
            authorizationFlow,
            tenantIdentifier,
            requestedClientId,
            user,
            availableAuthenticationTypes,
            requiredAnyOfAuthenticationTypes,
            createdAt,
            expiredAt);
    AuthenticationInteractionResults interactionResults = new AuthenticationInteractionResults();
    return new AuthenticationTransaction(identifier, request, interactionResults);
  }

  static User toUser(Map<String, String> map) {
    if (map.containsKey("user") && map.get("user") != null) {
      return jsonConverter.read(map.get("user"), User.class);
    }
    return User.notFound();
  }

  static List<String> toRequiredAnyOfAuthenticationTypes(Map<String, String> map) {
    if (map.containsKey("required_any_of_authentication_types")
        && map.get("required_any_of_authentication_types") != null) {
      return jsonConverter.read(map.get("required_any_of_authentication_types"), List.class);
    }
    return List.of();
  }
}
