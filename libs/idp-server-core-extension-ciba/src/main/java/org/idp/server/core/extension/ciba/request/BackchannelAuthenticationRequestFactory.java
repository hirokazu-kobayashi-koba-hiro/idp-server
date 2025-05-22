/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.request;

import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface BackchannelAuthenticationRequestFactory {
  BackchannelAuthenticationRequest create(
      Tenant tenant,
      CibaProfile profile,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  default BackchannelAuthenticationRequestIdentifier createIdentifier() {
    return new BackchannelAuthenticationRequestIdentifier(UUID.randomUUID().toString());
  }
}
