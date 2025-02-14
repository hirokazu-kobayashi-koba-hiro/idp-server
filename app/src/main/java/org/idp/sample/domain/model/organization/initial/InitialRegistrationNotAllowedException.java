package org.idp.sample.domain.model.organization.initial;

public class InitialRegistrationNotAllowedException extends RuntimeException {
  public InitialRegistrationNotAllowedException(String message) {
    super(message);
  }
}
