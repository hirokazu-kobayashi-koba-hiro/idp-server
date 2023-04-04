package org.idp.server.handler.token;

import org.idp.server.core.oauth.TokenRequestContext;
import org.idp.server.core.oauth.authenticator.ClientSecretBasicAuthenticator;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.token.OAuthToken;
import org.idp.server.core.oauth.token.OAuthTokenCreationService;
import org.idp.server.core.oauth.validator.TokenRequestCodeGrantValidator;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.type.AuthorizationCode;

public class TokenCreationCodeGrantService implements OAuthTokenCreationService {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  TokenRequestCodeGrantValidator validator;

  public TokenCreationCodeGrantService(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.validator = new TokenRequestCodeGrantValidator();
  }

  @Override
  public OAuthToken create(TokenRequestContext tokenRequestContext) {
    validator.validate(tokenRequestContext);

    AuthorizationCode code = tokenRequestContext.code();
    AuthorizationCodeGrant authorizationCodeGrant = authorizationCodeGrantRepository.find(code);
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationCodeGrant.authorizationRequestIdentifier());

    // FIXME consider various client authentication type
    ClientSecretBasicAuthenticator clientSecretBasicAuthenticator =
        new ClientSecretBasicAuthenticator();
    clientSecretBasicAuthenticator.authenticate(tokenRequestContext);

    return new OAuthToken();
  }
}
