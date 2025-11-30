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

package org.idp.server.adapters.springboot.application.restapi;

import java.util.UUID;
import org.springframework.http.HttpHeaders;

/**
 * Interface for configuring FAPI interaction ID headers in HTTP responses.
 *
 * <p>This interface provides methods to handle the x-fapi-interaction-id header as required by FAPI
 * (Financial-grade API) specification.
 *
 * <p><b>FAPI-R-6.2.1-11 Requirements:</b>
 *
 * <ul>
 *   <li>Resource endpoints MUST return the x-fapi-interaction-id header in responses
 *   <li>If the request contains x-fapi-interaction-id, the same value MUST be returned
 *   <li>If the request does not contain x-fapi-interaction-id, a new UUID MUST be generated
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * @RestController
 * public class ResourceApi implements FapiInteractionIdConfigurable {
 *
 *   @GetMapping
 *   public ResponseEntity<?> get(
 *       @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
 *       ...) {
 *
 *     HttpHeaders headers = new HttpHeaders();
 *     addFapiInteractionId(headers, fapiInteractionId);
 *     headers.setContentType(MediaType.APPLICATION_JSON);
 *
 *     return new ResponseEntity<>(response, headers, HttpStatus.OK);
 *   }
 * }
 * }</pre>
 *
 * @see <a
 *     href="https://openid.net/specs/openid-financial-api-part-1-1_0.html#protected-resources-provisions">
 *     FAPI 1.0 - Part 1: Baseline Section 6.2.1</a>
 */
public interface FapiInteractionIdConfigurable {

  String FAPI_INTERACTION_ID_HEADER = "x-fapi-interaction-id";

  /**
   * Adds the x-fapi-interaction-id header to the response.
   *
   * <p>If the request contained an x-fapi-interaction-id header, the same value is used. Otherwise,
   * a new UUID is generated.
   *
   * @param headers the HttpHeaders to add the header to
   * @param requestInteractionId the x-fapi-interaction-id from the request (may be null)
   * @return the interaction ID that was set (for logging purposes)
   */
  default String addFapiInteractionId(HttpHeaders headers, String requestInteractionId) {
    String interactionId =
        (requestInteractionId != null && !requestInteractionId.isEmpty())
            ? requestInteractionId
            : UUID.randomUUID().toString();
    headers.set(FAPI_INTERACTION_ID_HEADER, interactionId);
    return interactionId;
  }
}
