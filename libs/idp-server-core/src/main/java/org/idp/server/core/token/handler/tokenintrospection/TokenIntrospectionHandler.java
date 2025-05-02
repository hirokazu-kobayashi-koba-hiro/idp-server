package org.idp.server.core.token.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.token.tokenintrospection.validator.TokenIntrospectionValidator;
import org.idp.server.core.token.tokenintrospection.verifier.TokenIntrospectionVerifier;

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
    TokenIntrospectionRequestStatus verifiedStatus = verifier.verify();

    if (!verifiedStatus.isOK()) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
    }

    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken);
    return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
  }

  OAuthToken find(TokenIntrospectionRequest request) {
    TokenIntrospectionRequestParameters parameters = request.toParameters();
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    Tenant tenant = request.tenant();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenEntity refreshTokenEntity = parameters.refreshToken();
      return oAuthTokenRepository.find(tenant, refreshTokenEntity);
    }
  }
}
