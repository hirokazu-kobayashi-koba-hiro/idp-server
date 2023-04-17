package org.idp.server.clientauthenticator;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientAuthenticationType;
import org.idp.server.type.oauth.ClientSecretBasic;

public interface BackchannelRequestContext {

  BackchannelRequestParameters parameters();

  ClientSecretBasic clientSecretBasic();

  boolean hasClientSecretBasic();

  ServerConfiguration serverConfiguration();

  ClientConfiguration clientConfiguration();

  ClientAuthenticationType clientAuthenticationType();
}
