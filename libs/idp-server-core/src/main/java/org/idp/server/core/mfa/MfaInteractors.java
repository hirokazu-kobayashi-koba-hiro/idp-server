package org.idp.server.core.mfa;

import java.util.Map;
import java.util.Objects;

public class MfaInteractors {

  Map<MfaInteractionType, MfaInteractor> values;

  public MfaInteractors(Map<MfaInteractionType, MfaInteractor> values) {
    this.values = values;
  }

  public MfaInteractor get(MfaInteractionType type) {
    MfaInteractor interactor = values.get(type);

    if (Objects.isNull(interactor)) {
      throw new MfaInteractorUnSupportedException("No OAuthInteractor found for type " + type);
    }

    return interactor;
  }
}
