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

package org.idp.server.account_linking.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;

/**
 * Outcome of one account linking operation, carrying the security event it should be recorded as.
 *
 * <p>Failures are returned rather than thrown so that the caller can record them. A rejected
 * browser binding or a mismatched operator is the signal that someone is trying to attach an
 * external account to another user; left as an exception, the only trace would be a stack trace in
 * the application log. The layer that decides a request failed is also the layer that knows which
 * event it was, so the event type travels with the result.
 *
 * <p>Exceptions remain for what is genuinely unforeseen.
 */
public class AccountLinkingResult {

  AccountLinkingStatus status;
  Map<String, Object> contents;
  String redirectUri;
  SecurityEventType eventType;
  User user;
  RequestedClientId requestedClientId;

  AccountLinkingResult(
      AccountLinkingStatus status,
      Map<String, Object> contents,
      String redirectUri,
      SecurityEventType eventType,
      User user,
      RequestedClientId requestedClientId) {
    this.status = status;
    this.contents = contents;
    this.redirectUri = redirectUri;
    this.eventType = eventType;
    this.user = user;
    this.requestedClientId = requestedClientId;
  }

  /** Success that is not worth an event on its own. */
  public static AccountLinkingResult success(
      AccountLinkingStatus status, Map<String, Object> contents, User user) {
    return new AccountLinkingResult(status, contents, null, null, user, noClient());
  }

  public static AccountLinkingResult success(
      AccountLinkingStatus status,
      Map<String, Object> contents,
      DefaultSecurityEventType eventType,
      User user) {
    return new AccountLinkingResult(
        status, contents, null, eventType.toEventType(), user, noClient());
  }

  /** Success that continues as a browser redirect. */
  public static AccountLinkingResult redirect(String redirectUri) {
    return new AccountLinkingResult(
        AccountLinkingStatus.REDIRECT, Map.of(), redirectUri, null, null, noClient());
  }

  public static AccountLinkingResult error(
      AccountLinkingStatus status, String errorDescription, DefaultSecurityEventType eventType) {
    return error(status, "invalid_request", errorDescription, eventType);
  }

  /** Error carrying the code the external identity provider used, rather than this server's. */
  public static AccountLinkingResult error(
      AccountLinkingStatus status,
      String error,
      String errorDescription,
      DefaultSecurityEventType eventType) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", error);
    contents.put("error_description", errorDescription);
    return new AccountLinkingResult(
        status,
        contents,
        null,
        eventType == null ? null : eventType.toEventType(),
        null,
        noClient());
  }

  /**
   * Returns a copy naming who the event should be attributed to.
   *
   * <p>The browser legs have no Bearer token, so the event has to be built from what the linking
   * session knows. A run of failures is only useful if it says which account and client were being
   * targeted.
   */
  public AccountLinkingResult withContext(RequestedClientId requestedClientId, User user) {
    return new AccountLinkingResult(
        status, contents, redirectUri, eventType, user, requestedClientId);
  }

  private static RequestedClientId noClient() {
    return new RequestedClientId("");
  }

  public AccountLinkingStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public boolean isRedirect() {
    return status.isRedirect() && redirectUri != null && !redirectUri.isEmpty();
  }

  public boolean isError() {
    return status.isError();
  }

  public SecurityEventType eventType() {
    return eventType;
  }

  public boolean hasEventType() {
    return eventType != null;
  }

  public User user() {
    return user;
  }

  public boolean hasUser() {
    return user != null && user.exists();
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }
}
