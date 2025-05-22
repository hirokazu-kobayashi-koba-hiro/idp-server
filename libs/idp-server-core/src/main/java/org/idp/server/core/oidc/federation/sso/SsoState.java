/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation.sso;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class SsoState implements Serializable, JsonReadable {
  String sessionId;
  String tenantId;
  String provider;

  public SsoState() {}

  public SsoState(String sessionId, String tenantId, String provider) {
    this.sessionId = sessionId;
    this.tenantId = tenantId;
    this.provider = provider;
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(sessionId);
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(provider);
  }
}
