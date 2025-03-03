package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.exception.RequestObjectInvalidException;

public class RequestObjectVerifier
    implements AuthorizationRequestExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isRequestParameterPattern() || context.isUnsignedRequestObject();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      verify(context.joseContext(), context.serverConfiguration(), context.clientConfiguration());
    } catch (RequestObjectInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object", exception.getMessage(), context);
    }
  }
}
