package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.request.AuthorizationRequest;

/**
 * 4.1.2. Authorization Response
 *
 * <p>If the resource owner grants the access request, the authorization server issues an
 * authorization code and delivers it to the client by adding the following parameters to the query
 * component of the redirection URI using the "application/x-www-form-urlencoded" format, per
 * Appendix B:
 *
 * <p>code REQUIRED.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2">4.1.2. Authorization
 *     Response</a>
 */
public class AuthorizationResponseCodeCreator
    implements AuthorizationResponseCreator,
        AuthorizationCodeCreatable,
        RedirectUriDecidable,
        ResponseModeDecidable,
        JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                context.responseMode(),
                decideResponseModeValue(context.responseType(), context.responseMode()),
                context.tokenIssuer())
            .add(authorizationCode);

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
