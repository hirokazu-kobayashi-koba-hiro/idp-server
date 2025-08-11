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

package org.idp.server.core.openid.federation.sso;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.idp.server.platform.json.JsonConverter;

public class SsoStateCoder {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public static String encode(SsoState ssoState) {
    String json = jsonConverter.write(ssoState);

    return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  public static SsoState decode(String state) {
    String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
    return jsonConverter.read(decoded, SsoState.class);
  }
}
