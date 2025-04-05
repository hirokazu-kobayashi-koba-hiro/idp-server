package org.idp.server.adapters.springboot.mfa.email;

import org.idp.server.core.mfa.*;

public class EmailAuthenticationInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.EMAIL_VERIFICATION.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {

    MfaTransactionQueryRepository mfaTransactionQueryRepository =
        container.resolve(MfaTransactionQueryRepository.class);
    return new EmailAuthenticationInteractor(mfaTransactionQueryRepository);
  }
}
