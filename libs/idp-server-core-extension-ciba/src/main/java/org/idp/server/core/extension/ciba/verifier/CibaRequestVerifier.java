/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.verifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.platform.exception.UnSupportedException;

public class CibaRequestVerifier {

  Map<CibaProfile, CibaVerifier> baseVerifiers;
  CibaRequestContext context;

  public CibaRequestVerifier(CibaRequestContext context) {
    this.baseVerifiers = new HashMap<>();
    this.baseVerifiers.put(CibaProfile.CIBA, new CibaRequestNormalProfileVerifier());
    this.baseVerifiers.put(CibaProfile.FAPI_CIBA, new CibaRequestFapiProfileVerifier());
    this.context = context;
  }

  public void verify() {
    CibaVerifier cibaVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(cibaVerifier)) {
      throw new UnSupportedException(
          String.format("unsupported ciba profile (%s)", context.profile().name()));
    }
    cibaVerifier.verify(context);
  }
}
