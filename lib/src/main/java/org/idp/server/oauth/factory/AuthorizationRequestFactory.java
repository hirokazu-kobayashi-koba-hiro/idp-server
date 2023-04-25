package org.idp.server.oauth.factory;

import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.oidc.ClaimsValue;

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
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      return claimsValue.exists()
          ? jsonParser.read(claimsValue.value(), ClaimsPayload.class)
          : new ClaimsPayload();
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }
}
