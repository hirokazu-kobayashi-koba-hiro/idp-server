package org.idp.server.ciba.service;

import org.idp.server.ciba.CibaRequestContext;
import org.idp.server.ciba.CibaRequestParameters;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientSecretBasic;

public interface CibaRequestContextService {

  CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
