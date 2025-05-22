/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.application;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationCommandRepository {

  void register(Tenant tenant, IdentityVerificationApplication application);

  void update(Tenant tenant, IdentityVerificationApplication application);

  void delete(Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);
}
