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

package org.idp.server.platform.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public class BasicAuth {
  String username;
  String password;

  public BasicAuth() {}

  public BasicAuth(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public boolean exists() {
    return exists(username) && exists(password);
  }

  boolean exists(String value) {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  /**
   * Whether the presented credentials match these configured ones, compared in constant time.
   *
   * <p>Use this to authenticate a request. {@link #equals} short-circuits on the first differing
   * character, so its running time depends on how much of the secret the caller got right.
   */
  public boolean matches(BasicAuth presented) {
    if (presented == null || !exists() || !presented.exists()) {
      return false;
    }
    boolean usernameMatches = constantTimeEquals(username, presented.username);
    boolean passwordMatches = constantTimeEquals(password, presented.password);
    return usernameMatches & passwordMatches;
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    BasicAuth basicAuth = (BasicAuth) o;
    return Objects.equals(username, basicAuth.username)
        && Objects.equals(password, basicAuth.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }
}
