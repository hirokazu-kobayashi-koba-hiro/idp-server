package org.idp.server.core.authentication;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.authentication.exception.AuthenticationInteractorUnSupportedException;

public class AuthenticationInteractors {

  Map<AuthenticationInteractionType, AuthenticationInteractor> values;

  public AuthenticationInteractors(
      Map<AuthenticationInteractionType, AuthenticationInteractor> values) {
    this.values = values;
  }

  public AuthenticationInteractor get(AuthenticationInteractionType type) {
    AuthenticationInteractor interactor = values.get(type);

    if (Objects.isNull(interactor)) {
      throw new AuthenticationInteractorUnSupportedException(
          "No OAuthInteractor found for type " + type.name());
    }

    return interactor;
  }
}
