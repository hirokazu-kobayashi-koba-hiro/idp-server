/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.oauth.ClientAssertion;
import org.idp.server.basic.type.oauth.ClientAssertionType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;

public interface BackchannelRequestParameters {
  RequestedClientId clientId();

  boolean hasClientId();

  ClientSecret clientSecret();

  boolean hasClientSecret();

  ClientAssertion clientAssertion();

  boolean hasClientAssertion();

  ClientAssertionType clientAssertionType();

  boolean hasClientAssertionType();
}
