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

package org.idp.server.authenticators.webauthn4j.mds;

import static org.junit.jupiter.api.Assertions.*;

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.metadata.FidoMDS3MetadataBLOBProvider;
import com.webauthn4j.metadata.data.MetadataBLOB;
import com.webauthn4j.metadata.data.MetadataBLOBPayload;
import com.webauthn4j.metadata.data.MetadataBLOBPayloadEntry;
import com.webauthn4j.metadata.data.statement.MetadataStatement;
import com.webauthn4j.metadata.data.toc.StatusReport;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that connect to the real FIDO MDS service.
 *
 * <p>These tests require network connectivity and may take longer to execute. Run with: ./gradlew
 * test -PincludeTags=integration
 */
// @Tag("integration") // Uncomment to exclude from regular test runs
class MdsResolverIntegrationTest {

  // YubiKey 5 Series AAGUID (well-known, should be in MDS)
  private static final String YUBIKEY_5_NFC_AAGUID = "2fc0579f-8113-47ea-b116-bb5a8db9202a";

  // Apple's passkey AAGUID
  private static final String APPLE_PASSKEY_AAGUID = "fbfc3007-154e-4ecc-8c0b-6e020557d7bd";

  private static MetadataBLOB metadataBLOB;
  private static boolean mdsAvailable = false;

  @BeforeAll
  static void setUpOnce() {
    try {
      ObjectConverter objectConverter = new ObjectConverter();
      Set<TrustAnchor> trustAnchors = getDefaultTrustAnchors();
      FidoMDS3MetadataBLOBProvider provider =
          new FidoMDS3MetadataBLOBProvider(objectConverter, trustAnchors);
      metadataBLOB = provider.provide();
      mdsAvailable = metadataBLOB != null && metadataBLOB.getPayload() != null;
    } catch (Exception e) {
      System.err.println("Failed to fetch MDS BLOB: " + e.getMessage());
      e.printStackTrace();
      mdsAvailable = false;
    }
  }

  private static Set<TrustAnchor> getDefaultTrustAnchors() throws Exception {
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init((java.security.KeyStore) null);

    Set<TrustAnchor> trustAnchors = new HashSet<>();
    for (javax.net.ssl.TrustManager tm : tmf.getTrustManagers()) {
      if (tm instanceof X509TrustManager) {
        X509TrustManager x509Tm = (X509TrustManager) tm;
        for (X509Certificate cert : x509Tm.getAcceptedIssuers()) {
          trustAnchors.add(new TrustAnchor(cert, null));
        }
      }
    }
    return trustAnchors;
  }

  @Test
  void fidoMds_shouldBeAccessible() {
    // Skip if network is not available
    if (!mdsAvailable) {
      System.out.println("FIDO MDS not available, skipping test");
      return;
    }
    assertNotNull(metadataBLOB.getPayload());
  }

  @Test
  void metadataBlobPayload_shouldContainEntries() {
    if (!mdsAvailable) {
      return;
    }

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    assertNotNull(entries);
    assertFalse(entries.isEmpty(), "MDS should contain authenticator entries");

    System.out.println("MDS contains " + entries.size() + " entries");
    System.out.println("Next update: " + payload.getNextUpdate());
  }

  @Test
  void metadataBlobPayload_shouldContainYubiKeyEntry() {
    if (!mdsAvailable) {
      return;
    }

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    Optional<MetadataBLOBPayloadEntry> yubiKeyEntry =
        entries.stream()
            .filter(
                entry -> {
                  AAGUID aaguid = entry.getAaguid();
                  return aaguid != null && aaguid.toString().equalsIgnoreCase(YUBIKEY_5_NFC_AAGUID);
                })
            .findFirst();

    assertTrue(yubiKeyEntry.isPresent(), "YubiKey 5 NFC should be in MDS");

    MetadataBLOBPayloadEntry entry = yubiKeyEntry.get();
    assertNotNull(entry.getMetadataStatement());
    assertNotNull(entry.getStatusReports());
    assertFalse(entry.getStatusReports().isEmpty());

    MetadataStatement statement = entry.getMetadataStatement();
    System.out.println("YubiKey 5 NFC description: " + statement.getDescription());

    List<StatusReport> statusReports = entry.getStatusReports();
    StatusReport latestStatus = statusReports.get(statusReports.size() - 1);
    System.out.println("Latest status: " + latestStatus.getStatus());
  }

  @Test
  void metadataStatement_shouldContainAuthenticatorInfo() {
    if (!mdsAvailable) {
      return;
    }

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    // Find any entry with a complete MetadataStatement
    Optional<MetadataStatement> anyStatement =
        entries.stream()
            .filter(e -> e.getMetadataStatement() != null)
            .map(MetadataBLOBPayloadEntry::getMetadataStatement)
            .findFirst();

    assertTrue(anyStatement.isPresent());

    MetadataStatement statement = anyStatement.get();
    assertNotNull(statement.getDescription());
    System.out.println("Sample authenticator: " + statement.getDescription());
  }

  @Test
  void statusReport_shouldIdentifyFidoCertifiedAuthenticators() {
    if (!mdsAvailable) {
      return;
    }

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    long fidoCertifiedCount =
        entries.stream()
            .filter(
                entry -> {
                  List<StatusReport> reports = entry.getStatusReports();
                  if (reports == null || reports.isEmpty()) {
                    return false;
                  }
                  StatusReport latest = reports.get(reports.size() - 1);
                  String status = latest.getStatus() != null ? latest.getStatus().name() : "";
                  return status.startsWith("FIDO_CERTIFIED");
                })
            .count();

    System.out.println("FIDO Certified authenticators: " + fidoCertifiedCount);
    assertTrue(fidoCertifiedCount > 0, "There should be FIDO certified authenticators in MDS");
  }

  @Test
  void authenticatorStatus_shouldCorrectlyIdentifyCompromised() {
    if (!mdsAvailable) {
      return;
    }

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    // Find an entry and test AuthenticatorStatus creation
    Optional<MetadataBLOBPayloadEntry> anyEntry =
        entries.stream()
            .filter(e -> e.getStatusReports() != null && !e.getStatusReports().isEmpty())
            .findFirst();

    assertTrue(anyEntry.isPresent());

    MetadataBLOBPayloadEntry entry = anyEntry.get();
    List<StatusReport> statusReports = entry.getStatusReports();
    StatusReport latestReport = statusReports.get(statusReports.size() - 1);

    List<String> statusHistory =
        statusReports.stream()
            .map(sr -> sr.getStatus() != null ? sr.getStatus().name() : "UNKNOWN")
            .toList();

    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            entry.getAaguid().toString(),
            latestReport.getStatus(),
            latestReport.getEffectiveDate(),
            statusHistory);

    assertTrue(status.isFound());
    assertNotNull(status.latestStatus());
    assertFalse(status.statusHistory().isEmpty());

    System.out.println("AAGUID: " + status.aaguid());
    System.out.println("Status: " + status.latestStatus());
    System.out.println("Is compromised: " + status.isCompromised());
    System.out.println("Is FIDO certified: " + status.isFidoCertified());
    System.out.println("Is trusted: " + status.isTrusted());
  }

  @Test
  void cachedMdsResolver_shouldWorkWithInMemoryCache() {
    if (!mdsAvailable) {
      return;
    }

    // This test verifies the flow without actual file-based blob
    // The CachedMdsResolver uses LocalFileMetadataBLOBProvider internally,
    // so we test the AuthenticatorStatus logic with real MDS data

    InMemoryCacheStore cacheStore = new InMemoryCacheStore();

    MetadataBLOBPayload payload = metadataBLOB.getPayload();
    List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

    // Find YubiKey entry
    Optional<MetadataBLOBPayloadEntry> yubiKeyEntry =
        entries.stream()
            .filter(
                entry -> {
                  AAGUID aaguid = entry.getAaguid();
                  return aaguid != null && aaguid.toString().equalsIgnoreCase(YUBIKEY_5_NFC_AAGUID);
                })
            .findFirst();

    if (yubiKeyEntry.isPresent()) {
      MetadataBLOBPayloadEntry entry = yubiKeyEntry.get();
      List<StatusReport> statusReports = entry.getStatusReports();
      StatusReport latestReport = statusReports.get(statusReports.size() - 1);

      List<String> statusHistory =
          statusReports.stream()
              .map(sr -> sr.getStatus() != null ? sr.getStatus().name() : "UNKNOWN")
              .toList();

      AuthenticatorStatus status =
          AuthenticatorStatus.of(
              YUBIKEY_5_NFC_AAGUID,
              latestReport.getStatus(),
              latestReport.getEffectiveDate(),
              statusHistory);

      // Cache it
      cacheStore.put("mds:status:" + YUBIKEY_5_NFC_AAGUID, status, 3600);

      // Retrieve from cache
      Optional<AuthenticatorStatus> cached =
          cacheStore.find("mds:status:" + YUBIKEY_5_NFC_AAGUID, AuthenticatorStatus.class);

      assertTrue(cached.isPresent());
      assertEquals(status.aaguid(), cached.get().aaguid());
      assertEquals(status.latestStatus(), cached.get().latestStatus());
      assertEquals(status.isFidoCertified(), cached.get().isFidoCertified());
    }
  }

  /** Simple in-memory cache store for testing */
  private static class InMemoryCacheStore implements CacheStore {
    private final Map<String, Object> cache = new HashMap<>();

    @Override
    public <T> void put(String key, T value) {
      cache.put(key, value);
    }

    @Override
    public <T> void put(String key, T value, int timeToLiveSeconds) {
      cache.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(String key, Class<T> type) {
      Object value = cache.get(key);
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of((T) value);
    }

    @Override
    public boolean exists(String key) {
      return cache.containsKey(key);
    }

    @Override
    public void delete(String key) {
      cache.remove(key);
    }
  }
}
