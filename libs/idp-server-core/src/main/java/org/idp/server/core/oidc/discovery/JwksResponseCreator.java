/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.discovery;

import java.util.Map;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JwkParser;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class JwksResponseCreator {

  AuthorizationServerConfiguration authorizationServerConfiguration;

  public JwksResponseCreator(AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    try {
      String jwks = authorizationServerConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JsonWebKeyInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
