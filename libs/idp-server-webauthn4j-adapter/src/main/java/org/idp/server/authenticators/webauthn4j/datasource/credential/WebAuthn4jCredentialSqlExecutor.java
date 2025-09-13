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

package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.util.List;
import java.util.Map;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;

public interface WebAuthn4jCredentialSqlExecutor {

  void register(WebAuthn4jCredential credential);

  List<Map<String, Object>> findAll(String userId);

  void updateSignCount(String credentialId, long signCount);

  void delete(String credentialId);
}
