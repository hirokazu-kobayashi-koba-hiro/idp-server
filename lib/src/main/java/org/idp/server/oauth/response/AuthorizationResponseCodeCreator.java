package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.AuthorizationCode;

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
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable, RedirectUriDecidable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                new ResponseModeValue("?"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(authorizationCode);
    return authorizationResponseBuilder.build();
  }
}
