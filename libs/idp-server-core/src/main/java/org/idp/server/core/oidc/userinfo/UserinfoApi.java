/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface UserinfoApi {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);
}
