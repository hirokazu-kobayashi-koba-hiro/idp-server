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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import org.idp.server.platform.security.ssrf.SsrfProtectionException;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.platform.system.config.SsrfProtectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsrfProtectedHttpClientTest {

  @Mock private HttpClient httpClient;
  @Mock private SystemConfigurationResolver systemConfigurationResolver;
  @Mock private SystemConfiguration systemConfiguration;

  private SsrfProtectedHttpClient ssrfProtectedHttpClient;

  @BeforeEach
  void setUp() {
    ssrfProtectedHttpClient = new SsrfProtectedHttpClient(httpClient, systemConfigurationResolver);
  }

  @Nested
  @DisplayName("SSRF Protection Enabled")
  class SsrfProtectionEnabledTests {

    @BeforeEach
    void setUpEnabledProtection() {
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(SsrfProtectionConfig.defaultConfig());
    }

    @Test
    @DisplayName("Should block request to localhost")
    void shouldBlockLocalhost() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://localhost/api")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("localhost", ex.blockedHost());
    }

    @Test
    @DisplayName("Should block request to 127.0.0.1")
    void shouldBlockLoopbackIp() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:8080/api")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("127.0.0.1", ex.blockedIp());
    }

    @Test
    @DisplayName("Should block request to private IP 10.0.0.1")
    void shouldBlockPrivateIp10() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://10.0.0.1/internal")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("10.0.0.1", ex.blockedIp());
    }

    @Test
    @DisplayName("Should block request to private IP 192.168.1.1")
    void shouldBlockPrivateIp192() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://192.168.1.1/router")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("192.168.1.1", ex.blockedIp());
    }

    @Test
    @DisplayName("Should block request to private IP 172.16.0.1")
    void shouldBlockPrivateIp172() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://172.16.0.1/docker")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("172.16.0.1", ex.blockedIp());
    }

    @Test
    @DisplayName("Should block request to AWS metadata endpoint")
    void shouldBlockAwsMetadata() {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://169.254.169.254/latest/meta-data/"))
              .GET()
              .build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("169.254.169.254", ex.blockedIp());
    }

    @Test
    @DisplayName("Should block request to IPv6 loopback")
    void shouldBlockIpv6Loopback() {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://[::1]/api")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertNotNull(ex.blockedIp());
    }
  }

  @Nested
  @DisplayName("SSRF Protection Disabled")
  class SsrfProtectionDisabledTests {

    @BeforeEach
    void setUpDisabledProtection() {
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(SsrfProtectionConfig.disabled());
    }

    @Test
    @DisplayName("Should not throw exception when SSRF protection is disabled")
    void shouldNotBlockWhenDisabled() throws Exception {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/api")).GET().build();

      // Should not throw SsrfProtectionException, may throw other exceptions
      // (network errors, etc.)
      try {
        ssrfProtectedHttpClient.send(request);
      } catch (SsrfProtectionException e) {
        fail("Should not throw SsrfProtectionException when protection is disabled");
      } catch (HttpNetworkErrorException e) {
        // Expected - network error because we're not actually connecting
      }
    }
  }

  @Nested
  @DisplayName("SSRF Protection with Bypass Hosts")
  class SsrfProtectionBypassTests {

    @Test
    @DisplayName("Should allow bypassed host")
    void shouldAllowBypassedHost() throws Exception {
      SsrfProtectionConfig configWithBypass =
          new SsrfProtectionConfig(true, Set.of("localhost", "127.0.0.1"), Set.of());
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(configWithBypass);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://localhost/api")).GET().build();

      // Should not throw SsrfProtectionException for bypassed host
      try {
        ssrfProtectedHttpClient.send(request);
      } catch (SsrfProtectionException e) {
        fail("Should not throw SsrfProtectionException for bypassed host: " + e.getMessage());
      } catch (HttpNetworkErrorException e) {
        // Expected - network error because we're not actually connecting
      }
    }

    @Test
    @DisplayName("Should still block non-bypassed private IPs")
    void shouldBlockNonBypassedPrivateIp() {
      SsrfProtectionConfig configWithBypass =
          new SsrfProtectionConfig(true, Set.of("localhost"), Set.of());
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(configWithBypass);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://10.0.0.1/internal")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("10.0.0.1", ex.blockedIp());
    }
  }

  @Nested
  @DisplayName("SSRF Protection with Allowlist")
  class SsrfProtectionAllowlistTests {

    @Test
    @DisplayName("Should block hosts not in allowlist when allowlist is configured")
    void shouldBlockNonAllowlistedHost() {
      SsrfProtectionConfig configWithAllowlist =
          new SsrfProtectionConfig(true, Set.of(), Set.of("api.example.com"));
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(configWithAllowlist);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://evil.com/steal")).GET().build();

      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("evil.com", ex.blockedHost());
    }

    @Test
    @DisplayName("Should block private IP even if allowlisted (DNS rebinding protection)")
    void shouldBlockPrivateIpEvenIfAllowlisted() {
      // Even if a host is in allowlist, if it resolves to a private IP, it should be blocked
      SsrfProtectionConfig configWithAllowlist =
          new SsrfProtectionConfig(true, Set.of(), Set.of("localhost"));
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(configWithAllowlist);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://localhost/api")).GET().build();

      // localhost resolves to 127.0.0.1 which is blocked (DNS rebinding protection)
      SsrfProtectionException ex =
          assertThrows(SsrfProtectionException.class, () -> ssrfProtectedHttpClient.send(request));

      assertNotNull(ex);
    }
  }

  @Nested
  @DisplayName("Exception Handling")
  class ExceptionHandlingTests {

    @BeforeEach
    void setUpDisabledProtection() {
      when(systemConfigurationResolver.resolve()).thenReturn(systemConfiguration);
      when(systemConfiguration.ssrf()).thenReturn(SsrfProtectionConfig.disabled());
    }

    @Test
    @DisplayName(
        "Should throw HttpNetworkErrorException with timeout message when HttpTimeoutException occurs")
    void shouldHandleHttpTimeoutException() throws Exception {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://api.example.com/data")).GET().build();

      when(httpClient.send(eq(request), any(HttpResponse.BodyHandler.class)))
          .thenThrow(new HttpTimeoutException("request timed out"));

      HttpNetworkErrorException ex =
          assertThrows(
              HttpNetworkErrorException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("HTTP request timed out", ex.getMessage());
      assertInstanceOf(HttpTimeoutException.class, ex.getCause());
    }

    @Test
    @DisplayName(
        "Should throw HttpNetworkErrorException with failed message when IOException occurs")
    void shouldHandleIOException() throws Exception {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://api.example.com/data")).GET().build();

      when(httpClient.send(eq(request), any(HttpResponse.BodyHandler.class)))
          .thenThrow(new IOException("connection reset"));

      HttpNetworkErrorException ex =
          assertThrows(
              HttpNetworkErrorException.class, () -> ssrfProtectedHttpClient.send(request));

      assertEquals("HTTP request failed", ex.getMessage());
      assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    @DisplayName("Should map HttpTimeoutException to 504 via HttpResponseResolver")
    void shouldMapHttpTimeoutExceptionTo504() throws Exception {
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://api.example.com/data")).GET().build();

      when(httpClient.send(eq(request), any(HttpResponse.BodyHandler.class)))
          .thenThrow(new HttpTimeoutException("request timed out"));

      HttpNetworkErrorException ex =
          assertThrows(
              HttpNetworkErrorException.class, () -> ssrfProtectedHttpClient.send(request));

      HttpRequestResult result = HttpResponseResolver.resolveException(ex);
      assertEquals(504, result.statusCode());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle null SystemConfigurationResolver gracefully")
    void shouldHandleNullResolver() throws Exception {
      SsrfProtectedHttpClient clientWithNullResolver =
          new SsrfProtectedHttpClient(httpClient, null);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/api")).GET().build();

      // Should not throw SsrfProtectionException when resolver is null (protection disabled)
      try {
        clientWithNullResolver.send(request);
      } catch (SsrfProtectionException e) {
        fail("Should not throw SsrfProtectionException when resolver is null");
      } catch (HttpNetworkErrorException e) {
        // Expected - network error
      }
    }

    @Test
    @DisplayName("Should handle null SystemConfiguration gracefully")
    void shouldHandleNullConfiguration() throws Exception {
      when(systemConfigurationResolver.resolve()).thenReturn(null);

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/api")).GET().build();

      // Should not throw SsrfProtectionException when configuration is null
      try {
        ssrfProtectedHttpClient.send(request);
      } catch (SsrfProtectionException e) {
        fail("Should not throw SsrfProtectionException when configuration is null");
      } catch (HttpNetworkErrorException e) {
        // Expected - network error
      }
    }
  }
}
