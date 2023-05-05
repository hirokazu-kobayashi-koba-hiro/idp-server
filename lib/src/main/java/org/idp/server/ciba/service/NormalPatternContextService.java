package org.idp.server.ciba.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.ciba.*;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.NormalRequestFactory;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.*;
import org.idp.server.type.oauth.ClientSecretBasic;

/** NormalPatternContextService */
public class NormalPatternContextService
    implements CibaRequestContextService, CibaProfileAnalyzable {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
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
            parameters,
            joseContext,
            filteredScopes,
            serverConfiguration,
            clientConfiguration);
    return new CibaRequestContext(
        pattern,
        clientSecretBasic,
        parameters,
        joseContext,
        backchannelAuthenticationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
