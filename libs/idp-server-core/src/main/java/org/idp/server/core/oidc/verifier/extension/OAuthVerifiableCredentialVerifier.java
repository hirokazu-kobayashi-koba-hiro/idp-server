package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.oidc.vc.VerifiableCredentialInvalidException;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;

public class OAuthVerifiableCredentialVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !(oAuthRequestContext.hasAuthorizationDetails()
        && oAuthRequestContext.authorizationDetails().hasVerifiableCredential());
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      VerifiableCredentialVerifier verifiableCredentialVerifier =
          new VerifiableCredentialVerifier(
              context.authorizationRequest().authorizationDetails(),
              context.serverConfiguration(),
              context.clientConfiguration());
      verifiableCredentialVerifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    } catch (VerifiableCredentialInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    }
  }
}
