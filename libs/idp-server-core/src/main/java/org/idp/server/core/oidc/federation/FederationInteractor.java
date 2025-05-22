/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation;

import org.idp.server.core.oidc.federation.io.FederationCallbackRequest;
import org.idp.server.core.oidc.federation.io.FederationRequestResponse;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationInteractor {

  FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider);

  FederationInteractionResult callback(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest callbackRequest,
      UserQueryRepository userQueryRepository);
}
