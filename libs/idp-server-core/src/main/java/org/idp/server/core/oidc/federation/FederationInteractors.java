/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

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
