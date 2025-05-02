package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.request.AuthorizationRequest;

public class AuthorizationResponseNoneCreator
    implements AuthorizationResponseCreator,
        RedirectUriDecidable,
        ResponseModeDecidable,
        JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
            decideRedirectUri(authorizationRequest, context.clientConfiguration()),
            context.responseMode(),
            decideResponseModeValue(context.responseType(), context.responseMode()),
            context.tokenIssuer());

    if (context.hasState()) {
      authorizationResponseBuilder.add(authorizationRequest.state());
    }

    if (context.isJwtMode()) {
      AuthorizationResponse authorizationResponse = authorizationResponseBuilder.build();
      JarmPayload jarmPayload =
          createResponse(
              authorizationResponse, context.serverConfiguration(), context.clientConfiguration());
      authorizationResponseBuilder.add(jarmPayload);
    }

    return authorizationResponseBuilder.build();
  }
}
