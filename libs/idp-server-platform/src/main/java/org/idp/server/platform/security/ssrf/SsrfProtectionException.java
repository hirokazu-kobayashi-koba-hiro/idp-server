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

package org.idp.server.platform.security.ssrf;

/**
 * Exception thrown when SSRF protection detects a blocked request.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>The target host resolves to a private/internal IP address
 *   <li>The target is a cloud metadata service endpoint
 *   <li>The URL scheme is not allowed (e.g., file://, gopher://)
 * </ul>
 */
public class SsrfProtectionException extends RuntimeException {

  private final String blockedHost;
  private final String blockedIp;
  private final PrivateIpRange matchedRange;

  public SsrfProtectionException(String message) {
    super(message);
    this.blockedHost = null;
    this.blockedIp = null;
    this.matchedRange = null;
  }

  public SsrfProtectionException(String message, String blockedHost) {
    super(message);
    this.blockedHost = blockedHost;
    this.blockedIp = null;
    this.matchedRange = null;
  }

  public SsrfProtectionException(
      String message, String blockedHost, String blockedIp, PrivateIpRange matchedRange) {
    super(message);
    this.blockedHost = blockedHost;
    this.blockedIp = blockedIp;
    this.matchedRange = matchedRange;
  }

  public String blockedHost() {
    return blockedHost;
  }

  public String blockedIp() {
    return blockedIp;
  }

  public PrivateIpRange matchedRange() {
    return matchedRange;
  }

  public boolean hasMatchedRange() {
    return matchedRange != null;
  }
}
