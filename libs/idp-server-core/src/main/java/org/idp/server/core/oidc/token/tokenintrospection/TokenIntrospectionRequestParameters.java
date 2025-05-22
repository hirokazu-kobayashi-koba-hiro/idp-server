/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.tokenintrospection;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.oauth.*;

/** TokenRequestParameters */
public class TokenIntrospectionRequestParameters {
  ArrayValueMap values;

  public TokenIntrospectionRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenIntrospectionRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenIntrospectionRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public AccessTokenEntity accessToken() {
    return new AccessTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public RefreshTokenEntity refreshToken() {
    return new RefreshTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public boolean hasToken() {
    return contains(OAuthRequestKey.token);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}
