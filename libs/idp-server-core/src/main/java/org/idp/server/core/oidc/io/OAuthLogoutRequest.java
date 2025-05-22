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
import org.idp.server.core.oidc.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthLogoutRequest {
  Tenant tenant;
  Map<String, String[]> params;

  public OAuthLogoutRequest() {
    this.params = Map.of();
  }

  public OAuthLogoutRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthLogoutParameters toParameters() {
    return new OAuthLogoutParameters(params);
  }
}
