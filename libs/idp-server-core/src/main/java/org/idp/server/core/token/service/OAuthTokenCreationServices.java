package org.idp.server.core.token.service;

import static org.idp.server.basic.type.oauth.GrantType.*;
import static org.idp.server.basic.type.oauth.GrantType.ciba;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.basic.type.oauth.GrantType;

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
      throw new UnSupportedException(
          String.format("unsupported grant_type (%s)", grantType.name()));
    }
    return oAuthTokenCreationService;
  }
}
