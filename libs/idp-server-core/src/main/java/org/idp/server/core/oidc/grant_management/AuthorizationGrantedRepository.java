/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.grant_management;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationGrantedRepository {

  void register(Tenant tenant, AuthorizationGranted authorizationGranted);

  AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user);

  void update(Tenant tenant, AuthorizationGranted authorizationGranted);
}
