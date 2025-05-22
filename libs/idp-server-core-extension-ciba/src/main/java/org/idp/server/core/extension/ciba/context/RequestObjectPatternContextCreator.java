/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.core.extension.ciba.*;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.RequestObjectPatternFactory;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextCreator implements CibaRequestContextCreator {

  RequestObjectPatternFactory requestObjectPatternFactory = new RequestObjectPatternFactory();
  JoseHandler joseHandler = new JoseHandler();

  @Override
  public CibaRequestContext create(
      Tenant tenant,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {

      JoseContext joseContext =
          joseHandler.handle(
              parameters.request().value(),
              clientConfiguration.jwks(),
              authorizationServerConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();

      CibaRequestPattern pattern = CibaRequestPattern.REQUEST_OBJECT;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);
      CibaProfile profile = analyze(filteredScopes, authorizationServerConfiguration);

      BackchannelAuthenticationRequest backchannelAuthenticationRequest =
          requestObjectPatternFactory.create(
              tenant,
              profile,
              clientSecretBasic,
              parameters,
              joseContext,
              filteredScopes,
              authorizationServerConfiguration,
              clientConfiguration);

      return new CibaRequestContext(
          tenant,
          pattern,
          clientSecretBasic,
          clientCert,
          parameters,
          new CibaRequestObjectParameters(joseContext.claims().payload()),
          joseContext,
          backchannelAuthenticationRequest,
          authorizationServerConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object", exception.getMessage(), exception);
    }
  }
}
