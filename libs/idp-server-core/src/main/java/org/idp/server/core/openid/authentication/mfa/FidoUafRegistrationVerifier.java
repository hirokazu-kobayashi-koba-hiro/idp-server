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

package org.idp.server.core.openid.authentication.mfa;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.io.MfaRegistrationRequest;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationDeviceRule;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationPolicy;

public class FidoUafRegistrationVerifier implements MfaRequestVerifier {

  @Override
  public MfaVerificationResult verify(
      User user, MfaRegistrationRequest registrationRequest, AuthenticationPolicy policy) {
    AuthenticationDeviceRule authenticationDeviceRule = policy.authenticationDeviceRule();
    int authenticationDeviceCount = user.authenticationDeviceCount();
    int maxDevices = authenticationDeviceRule.maxDevices();

    if (authenticationDeviceCount >= maxDevices) {
      Map<String, Object> errors = new HashMap<>();
      errors.put("error", "invalid_request");
      errors.put(
          "error_description",
          String.format(
              "Maximum number of devices reached %d, user has already %d devices.",
              maxDevices, authenticationDeviceCount));

      return MfaVerificationResult.failure(errors);
    }

    return MfaVerificationResult.success();
  }
}
