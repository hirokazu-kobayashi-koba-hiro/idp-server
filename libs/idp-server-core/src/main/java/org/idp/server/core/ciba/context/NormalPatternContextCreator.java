package org.idp.server.core.ciba.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.NormalRequestFactory;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;

/** NormalPatternContextService */
public class NormalPatternContextCreator implements CibaRequestContextCreator {

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
