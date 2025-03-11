package org.idp.server.core.clientauthenticator;

import org.idp.server.core.type.oauth.ClientAssertion;
import org.idp.server.core.type.oauth.ClientAssertionType;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecret;

public interface BackchannelRequestParameters {
  ClientId clientId();

  boolean hasClientId();

  ClientSecret clientSecret();

  boolean hasClientSecret();

  ClientAssertion clientAssertion();

  boolean hasClientAssertion();

  ClientAssertionType clientAssertionType();

  boolean hasClientAssertionType();
}
