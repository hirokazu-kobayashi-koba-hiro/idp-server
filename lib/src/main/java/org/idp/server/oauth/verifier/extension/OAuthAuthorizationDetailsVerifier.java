package org.idp.server.oauth.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.rar.AuthorizationDetailsInvalidException;

public class OAuthAuthorizationDetailsVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.hasAuthorizationDetails();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      AuthorizationDetailsVerifier authorizationDetailsVerifier =
          new AuthorizationDetailsVerifier(
              context.authorizationRequest().authorizationDetails(),
              context.serverConfiguration(),
              context.clientConfiguration());
      authorizationDetailsVerifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    }
  }
}
