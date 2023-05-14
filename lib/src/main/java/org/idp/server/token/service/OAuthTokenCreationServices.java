package org.idp.server.token.service;

import static org.idp.server.type.oauth.GrantType.*;
import static org.idp.server.type.oauth.GrantType.ciba;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.GrantType;

public class OAuthTokenCreationServices {

  Map<GrantType, OAuthTokenCreationService> values = new HashMap<>();

  public OAuthTokenCreationServices(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenRepository oAuthTokenRepository) {
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
    values.put(
        ciba,
        new CibaGrantService(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            oAuthTokenRepository,
            authorizationGrantedRepository));
  }

  public OAuthTokenCreationService get(GrantType grantType) {
    OAuthTokenCreationService oAuthTokenCreationService = values.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new RuntimeException(String.format("unsupported grant_type (%s)", grantType.name()));
    }
    return oAuthTokenCreationService;
  }
}
