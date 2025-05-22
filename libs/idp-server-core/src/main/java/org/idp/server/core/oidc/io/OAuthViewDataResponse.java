/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.io;

import java.util.Map;
import org.idp.server.core.oidc.view.OAuthViewData;

public class OAuthViewDataResponse {
  OAuthViewDataStatus status;
  OAuthViewData oAuthViewData;

  public OAuthViewDataResponse(OAuthViewDataStatus status, OAuthViewData oAuthViewData) {
    this.status = status;
    this.oAuthViewData = oAuthViewData;
  }

  public OAuthViewDataStatus status() {
    return status;
  }

  public OAuthViewData oAuthViewData() {
    return oAuthViewData;
  }

  public Map<String, Object> contents() {
    return oAuthViewData.contents();
  }
}
