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
package org.idp.server.core.openid.authentication.interaction.post_hook;

import org.idp.server.platform.json.JsonReadable;

/** A rule of a {@code match} verification, which compares the values at two JSONPaths. */
public class MatchVerificationRule implements JsonReadable {

  String path;
  String operation;
  String targetPath;

  public MatchVerificationRule() {}

  public String path() {
    return path;
  }

  /** Returns the operation, which defaults to {@code eq} when omitted. */
  public String operation() {
    if (operation == null || operation.isEmpty()) {
      return "eq";
    }
    return operation;
  }

  public String targetPath() {
    return targetPath;
  }

  public boolean hasPaths() {
    return path != null && !path.isEmpty() && targetPath != null && !targetPath.isEmpty();
  }
}
