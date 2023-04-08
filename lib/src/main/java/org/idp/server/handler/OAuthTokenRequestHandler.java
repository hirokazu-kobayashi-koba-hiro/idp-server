package org.idp.server.handler;

import static org.idp.server.core.type.oauth.GrantType.authorization_code;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.OAuthTokenCreationService;
import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.token.validator.TokenRequestValidator;
import org.idp.server.core.type.oauth.GrantType;
import org.idp.server.service.TokenCreationCodeGrantService;

public class OAuthTokenRequestHandler {

  Map<GrantType, OAuthTokenCreationService> map = new HashMap<>();
  TokenRequestValidator tokenRequestValidator = new TokenRequestValidator();

  public OAuthTokenRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository) {
    map.put(
        authorization_code,
        new TokenCreationCodeGrantService(
            authorizationRequestRepository, authorizationCodeGrantRepository));
  }

  public OAuthToken handle(TokenRequestContext tokenRequestContext) {
    tokenRequestValidator.validate(tokenRequestContext);

    GrantType grantType = tokenRequestContext.grantType();
    OAuthTokenCreationService oAuthTokenCreationService = map.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new RuntimeException(String.format("unsupported grant_type (%s)", grantType.name()));
    }
    return oAuthTokenCreationService.create(tokenRequestContext);
  }
}
