package org.idp.server.core.extension.ciba.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.core.extension.ciba.*;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.NormalRequestFactory;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

/** NormalPatternContextService */
public class NormalPatternContextCreator implements CibaRequestContextCreator {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public CibaRequestContext create(
      Tenant tenant,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    JoseContext joseContext = new JoseContext();
    CibaRequestPattern pattern = CibaRequestPattern.NORMAL;
    Set<String> filteredScopes =
        filterScopes(pattern, parameters, joseContext, clientConfiguration);
    CibaProfile profile = analyze(filteredScopes, authorizationServerConfiguration);

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        normalRequestFactory.create(
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
        new CibaRequestObjectParameters(),
        joseContext,
        backchannelAuthenticationRequest,
        authorizationServerConfiguration,
        clientConfiguration);
  }
}
