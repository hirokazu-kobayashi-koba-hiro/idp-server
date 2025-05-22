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


package org.idp.server.core.oidc.userinfo.handler.io;

import java.util.Map;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.userinfo.UserinfoErrorResponse;
import org.idp.server.core.oidc.userinfo.UserinfoResponse;

public class UserinfoRequestResponse {
  UserinfoRequestStatus status;
  OAuthToken oAuthToken;
  UserinfoResponse userinfoResponse;
  UserinfoErrorResponse errorResponse;

  public UserinfoRequestResponse(
      UserinfoRequestStatus status, OAuthToken oAuthToken, UserinfoResponse userinfoResponse) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.userinfoResponse = userinfoResponse;
    this.errorResponse = new UserinfoErrorResponse();
  }

  public UserinfoRequestResponse(
      UserinfoRequestStatus status, UserinfoErrorResponse errorResponse) {
    this.status = status;
    this.userinfoResponse = new UserinfoResponse();
    this.errorResponse = errorResponse;
  }

  public UserinfoRequestStatus status() {
    return status;
  }

  public Map<String, Object> response() {
    if (status.isOK()) {
      return userinfoResponse.response();
    }
    return errorResponse.response();
  }

  public UserinfoResponse userinfoResponse() {
    return userinfoResponse;
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public User user() {
    return userinfoResponse.user();
  }

  public UserinfoErrorResponse errorResponse() {
    return errorResponse;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isOK() {
    return status.isOK();
  }
}
