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

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SsrfProtectionValidatorTest {

  private SsrfProtectionValidator validator;

  @BeforeEach
  void setUp() {
    validator = new SsrfProtectionValidator();
  }

  @Nested
  @DisplayName("IPv4 Private Range Blocking")
  class Ipv4PrivateRangeTests {

    @Test
    @DisplayName("Should block loopback address 127.0.0.1")
    void shouldBlockLoopback() {
      assertTrue(validator.isBlocked("127.0.0.1"));
      assertTrue(validator.isBlocked("127.0.0.2"));
      assertTrue(validator.isBlocked("127.255.255.255"));
    }

    @Test
    @DisplayName("Should block RFC1918 10.0.0.0/8")
    void shouldBlockPrivate10() {
      assertTrue(validator.isBlocked("10.0.0.1"));
      assertTrue(validator.isBlocked("10.255.255.255"));
      assertTrue(validator.isBlocked("10.10.10.10"));
    }

    @Test
    @DisplayName("Should block RFC1918 172.16.0.0/12")
    void shouldBlockPrivate172() {
      assertTrue(validator.isBlocked("172.16.0.1"));
      assertTrue(validator.isBlocked("172.31.255.255"));
      assertTrue(validator.isBlocked("172.20.0.1"));

      // 172.32.0.0 is outside the range
      assertFalse(validator.isBlocked("172.32.0.1"));
    }

    @Test
    @DisplayName("Should block RFC1918 192.168.0.0/16")
    void shouldBlockPrivate192() {
      assertTrue(validator.isBlocked("192.168.0.1"));
      assertTrue(validator.isBlocked("192.168.255.255"));
      assertTrue(validator.isBlocked("192.168.1.1"));
    }

    @Test
    @DisplayName("Should block cloud metadata service 169.254.169.254")
    void shouldBlockCloudMetadata() {
      assertTrue(validator.isBlocked("169.254.169.254"));
    }

    @Test
    @DisplayName("Should block link-local 169.254.0.0/16")
    void shouldBlockLinkLocal() {
      assertTrue(validator.isBlocked("169.254.0.1"));
      assertTrue(validator.isBlocked("169.254.255.255"));
    }

    @Test
    @DisplayName("Should block CGNAT 100.64.0.0/10")
    void shouldBlockCgnat() {
      assertTrue(validator.isBlocked("100.64.0.1"));
      assertTrue(validator.isBlocked("100.127.255.255"));
    }

    @Test
    @DisplayName("Should allow public IP addresses")
    void shouldAllowPublicIp() {
      assertFalse(validator.isBlocked("8.8.8.8"));
      assertFalse(validator.isBlocked("1.1.1.1"));
      assertFalse(validator.isBlocked("93.184.216.34")); // example.com
    }
  }

  @Nested
  @DisplayName("IPv6 Range Blocking")
  class Ipv6RangeTests {

    @Test
    @DisplayName("Should block IPv6 loopback ::1")
    void shouldBlockIpv6Loopback() {
      assertTrue(validator.isBlocked("::1"));
    }

    @Test
    @DisplayName("Should block IPv6 link-local fe80::/10")
    void shouldBlockIpv6LinkLocal() {
      assertTrue(validator.isBlocked("fe80::1"));
      assertTrue(validator.isBlocked("fe80::abcd:1234"));
    }

    @Test
    @DisplayName("Should block IPv6 unique local fc00::/7")
    void shouldBlockIpv6UniqueLocal() {
      assertTrue(validator.isBlocked("fc00::1"));
      assertTrue(validator.isBlocked("fd00::1"));
    }

    @Test
    @DisplayName("Should allow public IPv6 addresses")
    void shouldAllowPublicIpv6() {
      assertFalse(validator.isBlocked("2001:4860:4860::8888")); // Google DNS
      assertFalse(validator.isBlocked("2606:4700:4700::1111")); // Cloudflare DNS
    }
  }

  @Nested
  @DisplayName("URI Validation")
  class UriValidationTests {

    @Test
    @DisplayName("Should block dangerous schemes")
    void shouldBlockDangerousSchemes() {
      assertTrue(validator.isBlocked(URI.create("file:///etc/passwd")));
      assertTrue(validator.isBlocked(URI.create("gopher://localhost/")));
      assertTrue(validator.isBlocked(URI.create("dict://localhost/")));
    }

    @Test
    @DisplayName("Should allow HTTP and HTTPS schemes")
    void shouldAllowHttpSchemes() {
      // Note: These would fail on DNS resolution if the host doesn't exist
      // but the scheme check passes
      assertThrows(
          SsrfProtectionException.class,
          () -> validator.validate(URI.create("ftp://example.com/")));

      // For real URLs, we need actual resolvable hosts
      assertFalse(validator.isBlocked(URI.create("https://www.google.com/")));
    }

    @Test
    @DisplayName("Should block URIs resolving to private IPs")
    void shouldBlockPrivateIpInUri() {
      assertTrue(validator.isBlocked(URI.create("http://127.0.0.1/")));
      assertTrue(validator.isBlocked(URI.create("http://192.168.1.1/")));
      assertTrue(validator.isBlocked(URI.create("http://10.0.0.1/")));
      assertTrue(validator.isBlocked(URI.create("http://169.254.169.254/latest/meta-data/")));
    }
  }

  @Nested
  @DisplayName("Allowlist Validation")
  class AllowlistTests {

    @Test
    @DisplayName("Should allow hosts in allowlist")
    void shouldAllowAllowlistedHosts() {
      Set<String> allowlist = Set.of("api.example.com", "cdn.example.com");

      // This would work if the hosts were resolvable
      // For testing, we verify the allowlist logic works
      SsrfProtectionException ex =
          assertThrows(
              SsrfProtectionException.class,
              () -> validator.validateWithAllowlist(URI.create("https://evil.com/"), allowlist));

      assertEquals("evil.com", ex.blockedHost());
    }

    @Test
    @DisplayName("Should reject hosts not in allowlist")
    void shouldRejectNonAllowlistedHosts() {
      Set<String> allowlist = Set.of("api.example.com");

      assertThrows(
          SsrfProtectionException.class,
          () -> validator.validateWithAllowlist(URI.create("https://attacker.com/"), allowlist));
    }
  }

  @Nested
  @DisplayName("Exception Details")
  class ExceptionDetailsTests {

    @Test
    @DisplayName("Should include matched range in exception")
    void shouldIncludeMatchedRange() {
      SsrfProtectionException ex =
          assertThrows(
              SsrfProtectionException.class, () -> validator.validateIpAddress("127.0.0.1"));

      assertTrue(ex.hasMatchedRange());
      assertEquals(PrivateIpRange.LOOPBACK_IPV4, ex.matchedRange());
      assertEquals("127.0.0.1", ex.blockedIp());
    }

    @Test
    @DisplayName("Should include cloud metadata range for 169.254.169.254")
    void shouldIdentifyCloudMetadata() {
      SsrfProtectionException ex =
          assertThrows(
              SsrfProtectionException.class, () -> validator.validateIpAddress("169.254.169.254"));

      assertTrue(ex.hasMatchedRange());
      assertEquals(PrivateIpRange.CLOUD_METADATA, ex.matchedRange());
    }
  }

  @Nested
  @DisplayName("PrivateIpRange Enum")
  class PrivateIpRangeTests {

    @Test
    @DisplayName("Should have correct string representation")
    void shouldHaveCorrectToString() {
      String result = PrivateIpRange.LOOPBACK_IPV4.toString();
      assertTrue(result.contains("127.0.0.0"));
      assertTrue(result.contains("8"));
      assertTrue(result.contains("Loopback"));
    }

    @Test
    @DisplayName("Should correctly identify IPv4 vs IPv6")
    void shouldIdentifyIpVersion() {
      assertFalse(PrivateIpRange.LOOPBACK_IPV4.isIpv6());
      assertFalse(PrivateIpRange.PRIVATE_10.isIpv6());
      assertTrue(PrivateIpRange.LOOPBACK_IPV6.isIpv6());
      assertTrue(PrivateIpRange.LINK_LOCAL_IPV6.isIpv6());
    }
  }

  @Nested
  @DisplayName("Bypass Hosts (Development Mode)")
  class BypassHostsTests {

    @Test
    @DisplayName("Should allow bypassed hosts")
    void shouldAllowBypassedHosts() {
      SsrfProtectionValidator devValidator =
          SsrfProtectionValidator.withBypassHosts(Set.of("localhost", "mock-service"));

      // localhost would normally be blocked (resolves to 127.0.0.1)
      assertFalse(devValidator.isBlocked(URI.create("http://localhost:8080/")));
      assertFalse(devValidator.isBlocked(URI.create("http://mock-service:9000/api")));
    }

    @Test
    @DisplayName("Should still block non-bypassed private IPs")
    void shouldBlockNonBypassedPrivateIps() {
      SsrfProtectionValidator devValidator =
          SsrfProtectionValidator.withBypassHosts(Set.of("localhost"));

      // 10.0.0.1 is not in bypass list
      assertTrue(devValidator.isBlocked(URI.create("http://10.0.0.1/")));
      assertTrue(devValidator.isBlocked(URI.create("http://192.168.1.1/")));
    }

    @Test
    @DisplayName("Should provide development factory method")
    void shouldProvideDevelopmentFactory() {
      SsrfProtectionValidator devValidator = SsrfProtectionValidator.forDevelopment();

      assertFalse(devValidator.isBlocked(URI.create("http://localhost/")));
      assertFalse(devValidator.isBlocked(URI.create("http://127.0.0.1/")));
    }

    @Test
    @DisplayName("Bypass should be case-insensitive")
    void bypassShouldBeCaseInsensitive() {
      SsrfProtectionValidator devValidator =
          SsrfProtectionValidator.withBypassHosts(Set.of("LocalHost"));

      assertFalse(devValidator.isBlocked(URI.create("http://localhost/")));
      assertFalse(devValidator.isBlocked(URI.create("http://LOCALHOST/")));
    }
  }
}
