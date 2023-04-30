package org.idp.server.token.service;

import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.RefreshTokenGrantValidator;
import org.idp.server.token.verifier.RefreshTokenVerifier;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

public class RefreshTokenGrantService implements OAuthTokenCreationService, AccessTokenCreatable {

  OAuthTokenRepository oAuthTokenRepository;
  RefreshTokenGrantValidator validator;
  RefreshTokenVerifier verifier;

  public RefreshTokenGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.validator = new RefreshTokenGrantValidator();
    this.verifier = new RefreshTokenVerifier();
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    validator.validate(context);
    RefreshTokenValue refreshTokenValue = context.refreshToken();
    TokenIssuer tokenIssuer = context.tokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, refreshTokenValue);
    verifier.verify(context, oAuthToken);

    return new OAuthToken();
  }
}
