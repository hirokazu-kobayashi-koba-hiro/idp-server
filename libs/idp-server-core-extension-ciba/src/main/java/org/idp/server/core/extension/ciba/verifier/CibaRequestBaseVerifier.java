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


package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;

public class CibaRequestBaseVerifier {

  public void verify(CibaRequestContext context) {
    throwExceptionIfUnSupportedGrantType(context);
    throwExceptionIfNotContainsOpenidScope(context);
    throwExceptionIfNotContainsAnyHint(context);
  }

  void throwExceptionIfUnSupportedGrantType(CibaRequestContext context) {
    if (!context.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "authorization server is unsupported ciba grant");
    }
    if (!context.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "client is unauthorized ciba grant");
    }
  }

  void throwExceptionIfNotContainsOpenidScope(CibaRequestContext context) {
    if (!context.hasOpenidScope()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_scope",
          "backchannel request does not contains openid scope. OpenID Connect implements authentication as an extension to OAuth 2.0 by including the openid scope value in the authorization requests.");
    }
  }

  void throwExceptionIfNotContainsAnyHint(CibaRequestContext context) {
    if (!context.hasAnyHint()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint");
    }
  }
}
