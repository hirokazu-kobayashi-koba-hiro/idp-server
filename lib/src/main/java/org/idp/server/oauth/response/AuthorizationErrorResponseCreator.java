package org.idp.server.oauth.response;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.OAuthDenyReason;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.type.oauth.RedirectUri;
import org.idp.server.type.oauth.TokenIssuer;

public class AuthorizationErrorResponseCreator
    implements RedirectUriDecidable, ResponseModeDecidable {

  AuthorizationRequest authorizationRequest;
  OAuthDenyReason denyReason;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public AuthorizationErrorResponseCreator(
      AuthorizationRequest authorizationRequest,
      OAuthDenyReason denyReason,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.denyReason = denyReason;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationErrorResponse create() {
    TokenIssuer tokenIssuer = serverConfiguration.tokenIssuer();
    RedirectUri redirectUri = decideRedirectUri(authorizationRequest, clientConfiguration);
    ResponseModeValue responseModeValue =
        decideResponseModeValue(
            authorizationRequest.responseType(), authorizationRequest.responseMode());

    return new AuthorizationErrorResponseBuilder(redirectUri, responseModeValue, tokenIssuer)
        .add(authorizationRequest.state())
        .add(new Error(denyReason.name()))
        .add(new ErrorDescription(denyReason.errorDescription()))
        .build();
  }
}
