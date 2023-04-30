package org.idp.server.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.tokenintrospection.validator.TokenIntrospectionValidator;
import org.idp.server.tokenintrospection.verifier.TokenIntrospectionVerifier;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenIntrospectionHandler {

  OAuthTokenRepository oAuthTokenRepository;
  TokenIntrospectionValidator validator;
  TokenIntrospectionVerifier verifier;

  public TokenIntrospectionHandler(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.validator = new TokenIntrospectionValidator();
    this.verifier = new TokenIntrospectionVerifier();
  }

  public TokenIntrospectionResponse handle(TokenIntrospectionRequest request) {
    validator.validate(request.toParameters());

    OAuthToken oAuthToken = find(request);
    verifier.verify(oAuthToken);
    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken.accessTokenPayload());
    return new TokenIntrospectionResponse(
        TokenIntrospectionRequestStatus.OK, oAuthToken.accessTokenPayload(), contents);
  }

  OAuthToken find(TokenIntrospectionRequest request) {
    TokenIntrospectionRequestParameters parameters = request.toParameters();
    AccessTokenValue accessTokenValue = parameters.accessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenValue);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenValue refreshTokenValue = parameters.refreshToken();
      return oAuthTokenRepository.find(tokenIssuer, refreshTokenValue);
    }
  }
}
