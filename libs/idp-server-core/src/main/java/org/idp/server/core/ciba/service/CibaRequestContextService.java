package org.idp.server.core.ciba.service;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.oauth.ClientSecretBasic;

public interface CibaRequestContextService {

  CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
