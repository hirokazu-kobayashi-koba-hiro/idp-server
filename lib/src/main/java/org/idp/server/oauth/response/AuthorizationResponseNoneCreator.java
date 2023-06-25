package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.JarmPayload;

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
                context.tokenIssuer())
            .add(authorizationRequest.state());

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
