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
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** NormalRequestFactory */
public class NormalRequestFactory implements BackchannelAuthenticationRequestFactory {

  @Override
  public BackchannelAuthenticationRequest create(
      Tenant tenant,
      CibaProfile profile,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    BackchannelAuthenticationRequestBuilder builder =
        new BackchannelAuthenticationRequestBuilder()
            .add(createIdentifier())
            .add(tenant.identifier())
            .add(profile)
            .add(clientConfiguration.backchannelTokenDeliveryMode())
            .add(new Scopes(filteredScopes))
            .add(parameters.idTokenHint())
            .add(parameters.loginHint())
            .add(parameters.loginHintToken())
            .add(parameters.acrValues())
            .add(parameters.bindingMessage())
            .add(parameters.request())
            .add(parameters.clientNotificationToken())
            .add(parameters.userCode())
            .add(parameters.requestedExpiry());

    if (parameters.hasClientId()) {
      builder.add(parameters.clientId());
    } else {
      builder.add(clientSecretBasic.clientId());
    }

    return builder.build();
  }
}
