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

package org.idp.server.core.openid.oauth.response;

import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.platform.http.HttpQueryParams;

public class AuthorizationErrorResponse {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
  JarmPayload jarmPayload;
  HttpQueryParams httpQueryParams;

  public AuthorizationErrorResponse() {}

  AuthorizationErrorResponse(
      RedirectUri redirectUri,
      ResponseModeValue responseModeValue,
      State state,
      TokenIssuer tokenIssuer,
      Error error,
      ErrorDescription errorDescription,
      JarmPayload jarmPayload,
      HttpQueryParams httpQueryParams) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.state = state;
    this.tokenIssuer = tokenIssuer;
    this.error = error;
    this.errorDescription = errorDescription;
    this.jarmPayload = jarmPayload;
    this.httpQueryParams = httpQueryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public State state() {
    return state;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }

  public JarmPayload jarmPayload() {
    return jarmPayload;
  }

  HttpQueryParams queryParams() {
    return httpQueryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), httpQueryParams.params());
  }
}
