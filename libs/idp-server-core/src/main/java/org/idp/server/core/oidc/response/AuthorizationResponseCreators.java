/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.response;

import static org.idp.server.core.oidc.type.oauth.ResponseType.*;
import static org.idp.server.core.oidc.type.oauth.ResponseType.none;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oidc.type.oauth.ResponseType;
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
