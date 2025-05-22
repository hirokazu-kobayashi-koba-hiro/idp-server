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


package org.idp.server.core.oidc.authentication;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class AuthenticationInteractionRequestResult {

  AuthenticationInteractionStatus status;
  AuthenticationInteractionType type;
  User user;
  Authentication authentication;
  Map<String, Object> response;
  DefaultSecurityEventType eventType;

  public static AuthenticationInteractionRequestResult clientError(
      Map<String, Object> response,
      AuthenticationInteractionType type,
      DefaultSecurityEventType eventType) {
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.CLIENT_ERROR, type, response, eventType);
  }

  public static AuthenticationInteractionRequestResult serverError(
      Map<String, Object> response,
      AuthenticationInteractionType type,
      DefaultSecurityEventType eventType) {
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SERVER_ERROR, type, response, eventType);
  }

  public AuthenticationInteractionRequestResult(
      AuthenticationInteractionStatus status,
      AuthenticationInteractionType type,
      Map<String, Object> response,
      DefaultSecurityEventType eventType) {
    this.status = status;
    this.type = type;
    this.response = response;
    this.eventType = eventType;
  }

  public AuthenticationInteractionRequestResult(
      AuthenticationInteractionStatus status,
      AuthenticationInteractionType type,
      User user,
      Authentication authentication,
      Map<String, Object> response,
      DefaultSecurityEventType eventType) {
    this.status = status;
    this.type = type;
    this.user = user;
    this.authentication = authentication;
    this.response = response;
    this.eventType = eventType;
  }

  public AuthenticationInteractionStatus status() {
    return status;
  }

  public boolean isSuccess() {
    return status.isSuccess();
  }

  public boolean isError() {
    return status.isError();
  }

  public AuthenticationInteractionType type() {
    return type;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public Map<String, Object> response() {
    return response;
  }

  public DefaultSecurityEventType eventType() {
    return eventType;
  }

  public String interactionTypeName() {
    return type.name();
  }

  public boolean hasUser() {
    return Objects.nonNull(user) && user.exists();
  }

  public boolean hasAuthentication() {
    return Objects.nonNull(authentication) && authentication.exists();
  }

  public int statusCode() {
    return this.status.statusCode();
  }

  public boolean isIdentifyUserEventType() {
    return eventType.isIdentifyUserEventType();
  }
}
