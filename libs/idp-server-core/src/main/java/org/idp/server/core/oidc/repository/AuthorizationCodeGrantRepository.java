/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.repository;

import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationCodeGrantRepository {
  void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode);

  void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);
}
