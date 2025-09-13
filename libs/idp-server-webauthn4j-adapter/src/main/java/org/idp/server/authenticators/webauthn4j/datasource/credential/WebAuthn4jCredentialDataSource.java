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
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredentialRepository;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredentials;

public class WebAuthn4jCredentialDataSource implements WebAuthn4jCredentialRepository {

  WebAuthn4jCredentialSqlExecutor executor;

  public WebAuthn4jCredentialDataSource(WebAuthn4jCredentialSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(WebAuthn4jCredential credential) {
    executor.register(credential);
  }

  @Override
  public WebAuthn4jCredentials findAll(String userId) {
    List<Map<String, Object>> results = executor.findAll(userId);

    if (Objects.isNull(results) || results.isEmpty()) {
      return new WebAuthn4jCredentials();
    }

    List<WebAuthn4jCredential> credentials =
        results.stream().map(ModelConverter::convert).collect(Collectors.toList());

    return new WebAuthn4jCredentials(credentials);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {
    executor.updateSignCount(credentialId, signCount);
  }

  @Override
  public void delete(String credentialId) {
    executor.delete(credentialId);
  }
}
