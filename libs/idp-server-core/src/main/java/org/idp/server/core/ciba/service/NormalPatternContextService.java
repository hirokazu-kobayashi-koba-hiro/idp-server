package org.idp.server.core.ciba.service;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.NormalRequestFactory;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientSecretBasic;

/** NormalPatternContextService */
public class NormalPatternContextService
    implements CibaRequestContextService, CibaProfileAnalyzable {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    JoseContext joseContext = new JoseContext();
    CibaRequestPattern pattern = CibaRequestPattern.NORMAL;
    Set<String> filteredScopes =
        filterScopes(pattern, parameters, joseContext, clientConfiguration);
    CibaProfile profile = analyze(filteredScopes, serverConfiguration);

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        normalRequestFactory.create(
            profile,
            clientSecretBasic,
            parameters,
            joseContext,
            filteredScopes,
            serverConfiguration,
            clientConfiguration);

    return new CibaRequestContext(
        pattern,
        clientSecretBasic,
        clientCert,
        parameters,
        new CibaRequestObjectParameters(),
        joseContext,
        backchannelAuthenticationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
