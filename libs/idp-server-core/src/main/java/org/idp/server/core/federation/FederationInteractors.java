package org.idp.server.core.federation;

import java.util.Map;
import org.idp.server.basic.exception.UnSupportedException;

public class FederationInteractors {

  Map<FederationType, FederationInteractor> executors;

  public FederationInteractors(Map<FederationType, FederationInteractor> executors) {
    this.executors = executors;
  }

  public FederationInteractor get(FederationType type) {
    FederationInteractor federationInteractor = executors.get(type);

    if (federationInteractor == null) {
      throw new UnSupportedException("Unknown SSO type: " + type.name());
    }

    return federationInteractor;
  }
}
