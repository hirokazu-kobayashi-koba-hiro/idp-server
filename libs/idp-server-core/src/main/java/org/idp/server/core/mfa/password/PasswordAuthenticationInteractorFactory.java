package org.idp.server.core.mfa.password;

import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;

public class PasswordAuthenticationInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteractionType.PASSWORD_AUTHENTICATION.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {

    PasswordVerificationDelegation passwordVerificationDelegation =
        container.resolve(PasswordVerificationDelegation.class);
    return new PasswordAuthenticationInteractor(passwordVerificationDelegation);
  }
}
