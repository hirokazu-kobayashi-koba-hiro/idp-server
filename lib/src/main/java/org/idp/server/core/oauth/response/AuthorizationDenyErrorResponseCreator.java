package org.idp.server.core.oauth.response;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.extension.JarmPayload;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;
import org.idp.server.core.type.oauth.RedirectUri;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.oidc.ResponseMode;

public class AuthorizationDenyErrorResponseCreator
    implements RedirectUriDecidable, ResponseModeDecidable, JarmCreatable {

  AuthorizationRequest authorizationRequest;
  OAuthDenyReason denyReason;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public AuthorizationDenyErrorResponseCreator(
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
    ResponseMode responseMode = authorizationRequest.responseMode();
    ResponseModeValue responseModeValue =
        decideResponseModeValue(authorizationRequest.responseType(), responseMode);

    AuthorizationErrorResponseBuilder responseBuilder =
        new AuthorizationErrorResponseBuilder(
                redirectUri, responseMode, responseModeValue, tokenIssuer)
            .add(authorizationRequest.state())
            .add(new Error(denyReason.name()))
            .add(new ErrorDescription(denyReason.errorDescription()));
    if (responseMode.isJwtMode()) {
      AuthorizationErrorResponse errorResponse = responseBuilder.build();
      JarmPayload jarmPayload =
          createResponse(errorResponse, serverConfiguration, clientConfiguration);
      responseBuilder.add(jarmPayload);
    }

    return responseBuilder.build();
  }
}
