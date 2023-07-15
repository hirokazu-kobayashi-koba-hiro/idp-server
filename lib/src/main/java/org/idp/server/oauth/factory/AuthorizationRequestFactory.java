package org.idp.server.oauth.factory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.rar.AuthorizationDetail;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.request.OAuthRequestParameters;
import org.idp.server.type.oidc.ClaimsValue;
import org.idp.server.type.rar.AuthorizationDetailsEntity;

/** AuthorizationRequestFactory */
public interface AuthorizationRequestFactory {
  AuthorizationRequest create(
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);

  default AuthorizationRequestIdentifier createIdentifier() {
    return new AuthorizationRequestIdentifier(UUID.randomUUID().toString());
  }

  default ClaimsPayload convertClaimsPayload(ClaimsValue claimsValue) {
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return claimsValue.exists()
          ? jsonConverter.read(claimsValue.value(), ClaimsPayload.class)
          : new ClaimsPayload();
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }

  default AuthorizationDetails convertAuthorizationDetails(
      AuthorizationDetailsEntity authorizationDetailsEntity) {
    if (!authorizationDetailsEntity.exists()) {
      return new AuthorizationDetails();
    }
    try {
      Object object = authorizationDetailsEntity.value();
      if (object instanceof String string) {
        JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
        List list = jsonConverter.read(string, List.class);
        List<Map> details = (List<Map>) list;
        List<AuthorizationDetail> authorizationDetailsList =
            details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
        return new AuthorizationDetails(authorizationDetailsList);
      }
      List<Map> details = (List<Map>) authorizationDetailsEntity.value();
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
