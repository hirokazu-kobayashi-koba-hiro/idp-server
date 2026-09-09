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

package org.idp.server.core.adapters.datasource.identity.email;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.email.EmailVerificationChallenge;
import org.idp.server.core.openid.identity.email.EmailVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.email.EmailVerificationOperation;
import org.idp.server.platform.date.LocalDateTimeParser;

class ModelConverter {

  static EmailVerificationChallenge convert(Map<String, String> result) {
    return new EmailVerificationChallenge(
        new EmailVerificationChallengeIdentifier(result.get("id")),
        new UserIdentifier(result.get("user_id")),
        EmailVerificationOperation.of(result.get("operation")),
        result.get("target_email"),
        result.get("verification_code"),
        Integer.parseInt(result.get("attempts")),
        parse(result.get("expires_at")));
  }

  private static LocalDateTime parse(String value) {
    return LocalDateTimeParser.parse(value);
  }
}
