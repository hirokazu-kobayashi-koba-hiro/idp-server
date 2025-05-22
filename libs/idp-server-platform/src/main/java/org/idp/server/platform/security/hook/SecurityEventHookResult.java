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


package org.idp.server.platform.security.hook;

import java.util.Map;
import java.util.UUID;

public class SecurityEventHookResult {

  SecurityEventHookResultIdentifier identifier;
  SecurityEventHookStatus status;
  SecurityEventHookType type;
  Map<String, Object> contents;

  public SecurityEventHookResult() {}

  public static SecurityEventHookResult success(
      SecurityEventHookType type, Map<String, Object> contents) {
    SecurityEventHookResultIdentifier identifier =
        new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    return new SecurityEventHookResult(identifier, SecurityEventHookStatus.SUCCESS, type, contents);
  }

  public static SecurityEventHookResult failure(
      SecurityEventHookType type, Map<String, Object> contents) {
    SecurityEventHookResultIdentifier identifier =
        new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    return new SecurityEventHookResult(identifier, SecurityEventHookStatus.FAILURE, type, contents);
  }

  public SecurityEventHookResult(
      SecurityEventHookResultIdentifier identifier,
      SecurityEventHookStatus status,
      SecurityEventHookType type,
      Map<String, Object> contents) {
    this.identifier = identifier;
    this.status = status;
    this.type = type;
    this.contents = contents;
  }

  public SecurityEventHookResultIdentifier identifier() {
    return identifier;
  }

  public SecurityEventHookStatus status() {
    return status;
  }

  public SecurityEventHookType type() {
    return type;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
