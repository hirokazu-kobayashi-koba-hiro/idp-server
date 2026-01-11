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

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates URLs and IP addresses to prevent Server-Side Request Forgery (SSRF) attacks.
 *
 * <p>Implements defense-in-depth controls based on OWASP recommendations:
 *
 * <ul>
 *   <li>Block requests to private/internal IP ranges
 *   <li>Block requests to cloud metadata services
 *   <li>Restrict allowed URL schemes to HTTP/HTTPS
 *   <li>Validate resolved IP addresses after DNS lookup
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * // Production: Block all private ranges
 * SsrfProtectionValidator validator = new SsrfProtectionValidator();
 *
 * // Validate URI before making request
 * URI targetUri = URI.create("https://example.com/api");
 * validator.validate(targetUri);
 *
 * // Development: Allow localhost for mock services
 * SsrfProtectionValidator devValidator = SsrfProtectionValidator.withBypassHosts(
 *     Set.of("localhost", "127.0.0.1", "mock-service"));
 *
 * // Validate with allowlist (OWASP recommended)
 * Set<String> allowedHosts = Set.of("api.example.com", "cdn.example.com");
 * validator.validateWithAllowlist(targetUri, allowedHosts);
 * }</pre>
 *
 * @see <a
 *     href="https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html">OWASP
 *     SSRF Prevention</a>
 */
public class SsrfProtectionValidator {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final Set<String> DANGEROUS_SCHEMES =
      Set.of("file", "gopher", "dict", "ftp", "ldap", "tftp");

  private final Set<PrivateIpRange> blockedRanges;
  private final Set<String> bypassHosts;
  private final boolean blockAllPrivateRanges;

  /** Creates a validator that blocks all private IP ranges. */
  public SsrfProtectionValidator() {
    this.blockedRanges = EnumSet.allOf(PrivateIpRange.class);
    this.bypassHosts = Collections.emptySet();
    this.blockAllPrivateRanges = true;
  }

  /**
   * Creates a validator with custom blocked ranges.
   *
   * @param ranges specific IP ranges to block
   */
  public SsrfProtectionValidator(Set<PrivateIpRange> ranges) {
    this.blockedRanges =
        ranges.isEmpty() ? EnumSet.noneOf(PrivateIpRange.class) : EnumSet.copyOf(ranges);
    this.bypassHosts = Collections.emptySet();
    this.blockAllPrivateRanges = false;
  }

  /** Private constructor for builder-style factory methods. */
  private SsrfProtectionValidator(Set<PrivateIpRange> ranges, Set<String> bypassHosts) {
    this.blockedRanges =
        ranges.isEmpty() ? EnumSet.noneOf(PrivateIpRange.class) : EnumSet.copyOf(ranges);
    this.bypassHosts = new HashSet<>();
    for (String host : bypassHosts) {
      this.bypassHosts.add(host.toLowerCase());
    }
    this.blockAllPrivateRanges = ranges.size() == PrivateIpRange.values().length;
  }

  /**
   * Creates a validator that allows specific hosts to bypass SSRF checks.
   *
   * <p>Use this for development/testing environments where you need to allow requests to local mock
   * services while still protecting against SSRF in general.
   *
   * <p><strong>Warning:</strong> Only use bypass hosts for trusted internal services. Never bypass
   * hosts based on user input.
   *
   * @param bypassHosts hostnames that should bypass SSRF protection (e.g., "localhost",
   *     "mock-service")
   * @return a validator with the specified bypass hosts
   */
  public static SsrfProtectionValidator withBypassHosts(Set<String> bypassHosts) {
    return new SsrfProtectionValidator(EnumSet.allOf(PrivateIpRange.class), bypassHosts);
  }

  /**
   * Creates a validator for development environments that allows localhost.
   *
   * <p>Allows: localhost, 127.0.0.1, ::1
   *
   * <p><strong>Warning:</strong> Do not use in production.
   *
   * @return a validator suitable for development environments
   */
  public static SsrfProtectionValidator forDevelopment() {
    return withBypassHosts(Set.of("localhost", "127.0.0.1", "::1"));
  }

  /**
   * Validates a URI for SSRF protection.
   *
   * <p>Performs the following checks:
   *
   * <ol>
   *   <li>Scheme validation (only HTTP/HTTPS allowed)
   *   <li>Host extraction and validation
   *   <li>DNS resolution and IP address validation
   * </ol>
   *
   * @param uri the URI to validate
   * @throws SsrfProtectionException if the URI is blocked
   */
  public void validate(URI uri) {
    validateScheme(uri);
    validateHost(uri);
  }

  /**
   * Validates a URI against an allowlist of trusted hosts.
   *
   * <p>This is the OWASP-recommended approach when the set of valid external hosts is known in
   * advance.
   *
   * @param uri the URI to validate
   * @param allowedHosts set of allowed hostnames
   * @throws SsrfProtectionException if the host is not in the allowlist
   */
  public void validateWithAllowlist(URI uri, Set<String> allowedHosts) {
    validateScheme(uri);

    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new SsrfProtectionException("URI has no host: " + uri);
    }

    String normalizedHost = host.toLowerCase();
    if (!allowedHosts.contains(normalizedHost)) {
      throw new SsrfProtectionException("Host not in allowlist: " + host, host);
    }

    // Still validate IP even for allowlisted hosts (DNS rebinding protection)
    validateResolvedIp(host);
  }

  /**
   * Validates the URL scheme.
   *
   * @param uri the URI to validate
   * @throws SsrfProtectionException if the scheme is not allowed
   */
  private void validateScheme(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null) {
      throw new SsrfProtectionException("URI has no scheme: " + uri);
    }

    String normalizedScheme = scheme.toLowerCase();

    if (DANGEROUS_SCHEMES.contains(normalizedScheme)) {
      throw new SsrfProtectionException("Dangerous scheme blocked: " + scheme, uri.getHost());
    }

    if (!ALLOWED_SCHEMES.contains(normalizedScheme)) {
      throw new SsrfProtectionException(
          "Scheme not allowed (only HTTP/HTTPS): " + scheme, uri.getHost());
    }
  }

  /**
   * Validates the host by resolving DNS and checking IP ranges.
   *
   * @param uri the URI containing the host
   * @throws SsrfProtectionException if the host resolves to a blocked IP
   */
  private void validateHost(URI uri) {
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new SsrfProtectionException("URI has no host: " + uri);
    }

    // Check if host is in bypass list (for development/testing)
    if (isHostBypassed(host)) {
      return;
    }

    validateResolvedIp(host);
  }

  /**
   * Checks if a host is in the bypass list.
   *
   * @param host the hostname to check
   * @return true if the host should bypass SSRF protection
   */
  private boolean isHostBypassed(String host) {
    if (bypassHosts.isEmpty()) {
      return false;
    }
    return bypassHosts.contains(host.toLowerCase());
  }

  /**
   * Resolves the hostname and validates all resolved IP addresses.
   *
   * @param host the hostname to resolve and validate
   * @throws SsrfProtectionException if any resolved IP is in a blocked range
   */
  private void validateResolvedIp(String host) {
    try {
      // Resolve all IP addresses for the host (A and AAAA records)
      InetAddress[] addresses = InetAddress.getAllByName(host);

      for (InetAddress address : addresses) {
        validateIpAddress(host, address);
      }
    } catch (UnknownHostException e) {
      throw new SsrfProtectionException("Failed to resolve hostname: " + host, host);
    }
  }

  /**
   * Validates a single IP address against blocked ranges.
   *
   * @param host the original hostname (for error messages)
   * @param address the resolved IP address
   * @throws SsrfProtectionException if the IP is in a blocked range
   */
  private void validateIpAddress(String host, InetAddress address) {
    String ipString = address.getHostAddress();

    for (PrivateIpRange range : blockedRanges) {
      if (range.contains(address)) {
        throw new SsrfProtectionException(
            String.format(
                "Blocked: Host '%s' resolves to private/reserved IP %s (%s)",
                host, ipString, range.description()),
            host,
            ipString,
            range);
      }
    }
  }

  /**
   * Validates an IP address string directly.
   *
   * @param ipAddress the IP address string to validate
   * @throws SsrfProtectionException if the IP is in a blocked range
   */
  public void validateIpAddress(String ipAddress) {
    try {
      InetAddress address = InetAddress.getByName(ipAddress);
      validateIpAddress(ipAddress, address);
    } catch (UnknownHostException e) {
      throw new SsrfProtectionException("Invalid IP address: " + ipAddress, ipAddress);
    }
  }

  /**
   * Checks if an IP address would be blocked without throwing an exception.
   *
   * @param ipAddress the IP address to check
   * @return true if the IP address would be blocked
   */
  public boolean isBlocked(String ipAddress) {
    try {
      validateIpAddress(ipAddress);
      return false;
    } catch (SsrfProtectionException e) {
      return true;
    }
  }

  /**
   * Checks if a URI would be blocked without throwing an exception.
   *
   * @param uri the URI to check
   * @return true if the URI would be blocked
   */
  public boolean isBlocked(URI uri) {
    try {
      validate(uri);
      return false;
    } catch (SsrfProtectionException e) {
      return true;
    }
  }

  /**
   * Returns the set of IP ranges that are blocked.
   *
   * @return unmodifiable set of blocked ranges
   */
  public Set<PrivateIpRange> blockedRanges() {
    return Set.copyOf(blockedRanges);
  }
}
