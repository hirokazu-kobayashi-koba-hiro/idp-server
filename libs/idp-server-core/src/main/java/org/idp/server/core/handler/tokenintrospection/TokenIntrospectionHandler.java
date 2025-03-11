package org.idp.server.core.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.tokenintrospection.validator.TokenIntrospectionValidator;
import org.idp.server.core.tokenintrospection.verifier.TokenIntrospectionVerifier;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;

public class TokenIntrospectionHandler {

  OAuthTokenRepository oAuthTokenRepository;

  public TokenIntrospectionHandler(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  public TokenIntrospectionResponse handle(TokenIntrospectionRequest request) {
    TokenIntrospectionValidator validator = new TokenIntrospectionValidator(request.toParameters());
    validator.validate();

    OAuthToken oAuthToken = find(request);
    TokenIntrospectionVerifier verifier = new TokenIntrospectionVerifier(oAuthToken);
    verifier.verify();

    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken);
    return new TokenIntrospectionResponse(TokenIntrospectionRequestStatus.OK, oAuthToken, contents);
  }

  OAuthToken find(TokenIntrospectionRequest request) {
    TokenIntrospectionRequestParameters parameters = request.toParameters();
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenEntity);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenEntity refreshTokenEntity = parameters.refreshToken();
      return oAuthTokenRepository.find(tokenIssuer, refreshTokenEntity);
    }
  }
}
