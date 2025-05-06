package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface BackchannelRequestContext {

  BackchannelRequestParameters parameters();

  ClientSecretBasic clientSecretBasic();

  ClientCert clientCert();

  boolean hasClientSecretBasic();

  AuthorizationServerConfiguration serverConfiguration();

  ClientConfiguration clientConfiguration();

  ClientAuthenticationType clientAuthenticationType();

  RequestedClientId requestedClientId();
}
