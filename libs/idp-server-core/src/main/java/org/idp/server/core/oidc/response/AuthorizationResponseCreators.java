/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.response;

import static org.idp.server.basic.type.oauth.ResponseType.*;
import static org.idp.server.basic.type.oauth.ResponseType.none;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.oauth.ResponseType;
import org.idp.server.platform.exception.UnSupportedException;

public class AuthorizationResponseCreators {
  Map<ResponseType, AuthorizationResponseCreator> values;

  public AuthorizationResponseCreators() {
    values = new HashMap<>();
    values.put(code, new AuthorizationResponseCodeCreator());
    values.put(token, new AuthorizationResponseTokenCreator());
    values.put(id_token, new AuthorizationResponseIdTokenCreator());
    values.put(code_token, new AuthorizationResponseCodeTokenCreator());
    values.put(code_token_id_token, new AuthorizationResponseCodeTokenIdTokenCreator());
    values.put(code_id_token, new AuthorizationResponseCodeIdTokenCreator());
    values.put(token_id_token, new AuthorizationResponseTokenIdTokenCreator());
    values.put(none, new AuthorizationResponseNoneCreator());
  }

  public AuthorizationResponseCreator get(ResponseType responseType) {
    AuthorizationResponseCreator authorizationResponseCreator = values.get(responseType);
    if (Objects.isNull(authorizationResponseCreator)) {
      throw new UnSupportedException(
          String.format("not support request type (%s)", responseType.value()));
    }
    return authorizationResponseCreator;
  }
}
