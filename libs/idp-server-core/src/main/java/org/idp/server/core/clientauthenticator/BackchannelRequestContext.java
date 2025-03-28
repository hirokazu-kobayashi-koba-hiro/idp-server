package org.idp.server.core.clientauthenticator;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientAuthenticationType;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecretBasic;

public interface BackchannelRequestContext {

  BackchannelRequestParameters parameters();

  ClientSecretBasic clientSecretBasic();

  ClientCert clientCert();

  boolean hasClientSecretBasic();

  ServerConfiguration serverConfiguration();

  ClientConfiguration clientConfiguration();

  ClientAuthenticationType clientAuthenticationType();

  ClientId clientId();
}
