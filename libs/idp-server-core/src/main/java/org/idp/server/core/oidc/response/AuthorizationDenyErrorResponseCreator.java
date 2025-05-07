package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.basic.type.extension.OAuthDenyReason;
import org.idp.server.basic.type.extension.ResponseModeValue;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.basic.type.oauth.RedirectUri;
import org.idp.server.basic.type.oauth.TokenIssuer;
import org.idp.server.basic.type.oidc.ResponseMode;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;

public class AuthorizationDenyErrorResponseCreator
    implements RedirectUriDecidable, ResponseModeDecidable, JarmCreatable {

  AuthorizationRequest authorizationRequest;
  OAuthDenyReason denyReason;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public AuthorizationDenyErrorResponseCreator(
      AuthorizationRequest authorizationRequest,
      OAuthDenyReason denyReason,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.denyReason = denyReason;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationErrorResponse create() {
    TokenIssuer tokenIssuer = authorizationServerConfiguration.tokenIssuer();
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
          createResponse(errorResponse, authorizationServerConfiguration, clientConfiguration);
      responseBuilder.add(jarmPayload);
    }

    return responseBuilder.build();
  }
}
