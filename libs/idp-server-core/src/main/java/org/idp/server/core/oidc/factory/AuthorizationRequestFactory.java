package org.idp.server.core.oidc.factory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oidc.ClaimsValue;
import org.idp.server.basic.type.rar.AuthorizationDetailsEntity;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.identity.RequestedClaimsPayload;
import org.idp.server.core.oidc.rar.AuthorizationDetail;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.request.OAuthRequestParameters;

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

  default RequestedClaimsPayload convertClaimsPayload(ClaimsValue claimsValue) {
    try {
      JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
      return claimsValue.exists()
          ? jsonConverter.read(claimsValue.value(), RequestedClaimsPayload.class)
          : new RequestedClaimsPayload();
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
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
        JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
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
