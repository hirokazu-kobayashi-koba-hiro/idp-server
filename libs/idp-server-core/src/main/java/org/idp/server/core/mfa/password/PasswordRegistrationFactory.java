package org.idp.server.core.mfa.password;

import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.identity.PasswordEncodeDelegation;

public class PasswordRegistrationFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.PASSWORD_REGISTRATION.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {

    PasswordEncodeDelegation passwordEncodeDelegation =
        container.resolve(PasswordEncodeDelegation.class);
    return new PasswordRegistrationInteractor(passwordEncodeDelegation);
  }
}
