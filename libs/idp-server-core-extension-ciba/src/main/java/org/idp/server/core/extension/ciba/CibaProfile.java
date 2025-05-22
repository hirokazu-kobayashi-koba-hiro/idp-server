/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba;

public enum CibaProfile {
  CIBA,
  FAPI_CIBA;

  public boolean isCiba() {
    return this == CIBA;
  }

  public boolean isFapiCiba() {
    return this == FAPI_CIBA;
  }
}
