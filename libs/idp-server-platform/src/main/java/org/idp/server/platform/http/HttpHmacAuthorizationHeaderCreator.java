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

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;

public class HttpHmacAuthorizationHeaderCreator {

  String apiKey;
  HmacHasher hmacHasher;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpHmacAuthorizationHeaderCreator.class);

  public HttpHmacAuthorizationHeaderCreator(String apiKey, String secret) {
    this.apiKey = apiKey;
    this.hmacHasher = new HmacHasher(secret);
  }

  public String create(
      String method,
      String url,
      Map<String, Object> body,
      List<String> signingFields,
      String signatureFormat) {

    String timestamp = Instant.now().toString();
    String path = URI.create(url).getPath();
    String bodyJson = JsonConverter.snakeCaseInstance().write(body != null ? body : Map.of());

    Map<String, String> context = new HashMap<>();
    context.put("method", method.toUpperCase());
    context.put("path", path);
    context.put("timestamp", timestamp);
    context.put("body", bodyJson);

    String payload =
        signingFields.stream()
            .map(
                field -> {
                  if (!context.containsKey(field)) {
                    return "";
                  }
                  return context.get(field);
                })
            .collect(Collectors.joining("\n"));

    String signature = hmacHasher.hash(payload);
    log.debug("HMAC Signature: {}", signature);

    return signatureFormat.replace("{signature}", signature);
  }
}
