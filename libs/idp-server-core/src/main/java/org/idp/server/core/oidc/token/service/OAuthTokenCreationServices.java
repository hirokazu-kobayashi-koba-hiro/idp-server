package org.idp.server.core.oidc.token.service;

import static org.idp.server.basic.type.oauth.GrantType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.exception.UnSupportedException;

public class OAuthTokenCreationServices {

  Map<GrantType, OAuthTokenCreationService> values = new HashMap<>();

  public OAuthTokenCreationServices(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      Map<GrantType, OAuthTokenCreationService> extensionOAuthTokenCreationServices) {
    values.put(
        authorization_code,
        new AuthorizationCodeGrantService(
            authorizationRequestRepository,
            oAuthTokenRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository));
    values.put(refresh_token, new RefreshTokenGrantService(oAuthTokenRepository));
    values.put(password, new ResourceOwnerPasswordCredentialsGrantService(oAuthTokenRepository));
    values.put(client_credentials, new ClientCredentialsGrantService(oAuthTokenRepository));
    values.putAll(extensionOAuthTokenCreationServices);
  }

  public OAuthTokenCreationService get(GrantType grantType) {
    OAuthTokenCreationService oAuthTokenCreationService = values.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new UnSupportedException(
          String.format("unsupported grant_type (%s)", grantType.name()));
    }
    return oAuthTokenCreationService;
  }
}
