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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SsoCredentialsParameterConfig {
  String clientId;
  String clientSecret;
  String clientAuthenticationType;
  String tokenEndpoint;
  long accessTokenExpiresIn;
  long refreshTokenExpiresIn;

  public SsoCredentialsParameterConfig() {}

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public long accessTokenExpiresIn() {
    return accessTokenExpiresIn;
  }

  public long refreshTokenExpiresIn() {
    return refreshTokenExpiresIn;
  }

  public boolean isClientSecretBasic() {
    return "client_secret_basic".equals(clientAuthenticationType);
  }

  public String basicAuthenticationValue() {
    String auth = clientId + ":" + clientSecret;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encodedAuth;
  }
}
