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

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.metadata.FidoMDS3MetadataBLOBProvider;
import com.webauthn4j.metadata.data.MetadataBLOB;
import com.webauthn4j.metadata.data.MetadataBLOBPayload;
import com.webauthn4j.metadata.data.MetadataBLOBPayloadEntry;
import com.webauthn4j.metadata.data.statement.MetadataStatement;
import java.security.KeyStore;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;

public class CachedMdsResolver implements MdsResolver {

  private static final LoggerWrapper LOG = LoggerWrapper.getLogger(CachedMdsResolver.class);
  private static final String CACHE_KEY_STATUS_PREFIX = "mds:status:";
  private static final String CACHE_KEY_LAST_FETCH = "mds:last_fetch";
  private static final String CACHE_KEY_MDS_ENTRIES = "mds:entries";
  private static final int DEFAULT_CACHE_TTL_SECONDS = 86400; // 24 hours

  private final MdsConfiguration configuration;
  private final CacheStore cacheStore;
  private final Map<String, MdsCacheEntry> entryMap;
  private final ObjectConverter objectConverter;
  private volatile boolean initialized;
  private volatile long lastFetchTime;

  public CachedMdsResolver(MdsConfiguration configuration, CacheStore cacheStore) {
    this.configuration = configuration;
    this.cacheStore = cacheStore;
    this.entryMap = new ConcurrentHashMap<>();
    this.objectConverter = new ObjectConverter();
    this.initialized = false;
    this.lastFetchTime = 0;
  }

  @Override
  public Optional<MetadataStatement> resolve(AAGUID aaguid) {
    if (aaguid == null) {
      return Optional.empty();
    }
    return resolve(aaguid.toString());
  }

  @Override
  public Optional<MetadataStatement> resolve(String aaguidString) {
    if (aaguidString == null || aaguidString.isEmpty()) {
      return Optional.empty();
    }

    ensureInitialized();

    MdsCacheEntry entry = entryMap.get(normalizeAaguid(aaguidString));
    if (entry == null) {
      return Optional.empty();
    }

    return entry.getMetadataStatement(objectConverter);
  }

  @Override
  public AuthenticatorStatus checkStatus(AAGUID aaguid) {
    if (aaguid == null) {
      return AuthenticatorStatus.notFound("unknown");
    }
    return checkStatus(aaguid.toString());
  }

  @Override
  public AuthenticatorStatus checkStatus(String aaguidString) {
    if (aaguidString == null || aaguidString.isEmpty()) {
      return AuthenticatorStatus.notFound("unknown");
    }

    ensureInitialized();

    String cacheKey = CACHE_KEY_STATUS_PREFIX + aaguidString;
    Optional<AuthenticatorStatus> cached = cacheStore.find(cacheKey, AuthenticatorStatus.class);

    if (cached.isPresent()) {
      return cached.get();
    }

    String normalizedAaguid = normalizeAaguid(aaguidString);
    MdsCacheEntry entry = entryMap.get(normalizedAaguid);

    if (entry == null) {
      AuthenticatorStatus status = AuthenticatorStatus.notFound(aaguidString);
      cacheStore.put(cacheKey, status, getCacheTtlSeconds());
      return status;
    }

    AuthenticatorStatus status = entry.toAuthenticatorStatus(aaguidString);
    cacheStore.put(cacheKey, status, getCacheTtlSeconds());
    return status;
  }

  @Override
  public boolean isCompromised(AAGUID aaguid) {
    return checkStatus(aaguid).isCompromised();
  }

  @Override
  public boolean isCompromised(String aaguidString) {
    return checkStatus(aaguidString).isCompromised();
  }

  @Override
  public synchronized void refresh() {
    initialized = false;
    lastFetchTime = 0;
    entryMap.clear();
    ensureInitialized();
  }

  private synchronized void ensureInitialized() {
    if (initialized && !isCacheExpired()) {
      return;
    }

    if (!configuration.enabled()) {
      LOG.info("MDS is not enabled");
      initialized = true;
      return;
    }

    // Try to restore from CacheStore first
    if (restoreFromCache()) {
      LOG.info("MDS restored from cache with {} entries", entryMap.size());
      initialized = true;
      return;
    }

    // Fetch from FIDO Alliance
    fetchFromFidoAlliance();
  }

  @SuppressWarnings("unchecked")
  private boolean restoreFromCache() {
    try {
      Optional<Long> cachedFetchTime = cacheStore.find(CACHE_KEY_LAST_FETCH, Long.class);
      if (cachedFetchTime.isEmpty()) {
        return false;
      }

      lastFetchTime = cachedFetchTime.get();
      if (isCacheExpired()) {
        LOG.debug("MDS cache expired, will fetch from FIDO Alliance");
        return false;
      }

      Optional<Map> cachedEntries = cacheStore.find(CACHE_KEY_MDS_ENTRIES, Map.class);
      if (cachedEntries.isEmpty()) {
        return false;
      }

      Map<String, Map<String, Object>> entriesMap = cachedEntries.get();
      entryMap.clear();
      for (Map.Entry<String, Map<String, Object>> entry : entriesMap.entrySet()) {
        entryMap.put(entry.getKey(), MdsCacheEntry.fromMap(entry.getValue()));
      }

      return !entryMap.isEmpty();
    } catch (Exception e) {
      LOG.warn("Failed to restore MDS from cache: {}", e.getMessage());
      return false;
    }
  }

  private void fetchFromFidoAlliance() {
    try {
      Set<TrustAnchor> trustAnchors = getDefaultTrustAnchors();
      FidoMDS3MetadataBLOBProvider provider =
          new FidoMDS3MetadataBLOBProvider(objectConverter, trustAnchors);

      LOG.info("Fetching MDS BLOB from FIDO Alliance...");
      MetadataBLOB blob = provider.provide();

      if (blob == null || blob.getPayload() == null) {
        LOG.warn("MDS BLOB is null or has no payload");
        initialized = true;
        return;
      }

      MetadataBLOBPayload payload = blob.getPayload();
      List<MetadataBLOBPayloadEntry> entries = payload.getEntries();

      if (entries == null) {
        LOG.warn("MDS BLOB has no entries");
        initialized = true;
        return;
      }

      entryMap.clear();
      Map<String, Map<String, Object>> cacheEntriesMap = new ConcurrentHashMap<>();

      for (MetadataBLOBPayloadEntry entry : entries) {
        AAGUID aaguid = entry.getAaguid();
        if (aaguid != null) {
          String normalizedAaguid = normalizeAaguid(aaguid.toString());
          MdsCacheEntry cacheEntry = MdsCacheEntry.from(entry, objectConverter);
          entryMap.put(normalizedAaguid, cacheEntry);
          cacheEntriesMap.put(normalizedAaguid, cacheEntry.toMap());
        }
      }

      lastFetchTime = System.currentTimeMillis();

      // Store in CacheStore
      cacheStore.put(CACHE_KEY_LAST_FETCH, lastFetchTime, getCacheTtlSeconds());
      cacheStore.put(CACHE_KEY_MDS_ENTRIES, cacheEntriesMap, getCacheTtlSeconds());

      LOG.info(
          "MDS fetched and cached with {} entries, nextUpdate: {}",
          entryMap.size(),
          payload.getNextUpdate());
      initialized = true;

    } catch (Exception e) {
      LOG.error("Failed to fetch MDS BLOB from FIDO Alliance: {}", e.getMessage(), e);
      initialized = true;
    }
  }

  private boolean isCacheExpired() {
    if (lastFetchTime == 0) {
      // Try to restore from cache
      Optional<Long> cached = cacheStore.find(CACHE_KEY_LAST_FETCH, Long.class);
      if (cached.isPresent()) {
        lastFetchTime = cached.get();
      }
    }

    if (lastFetchTime == 0) {
      return true;
    }

    long elapsedSeconds = (System.currentTimeMillis() - lastFetchTime) / 1000;
    return elapsedSeconds >= getCacheTtlSeconds();
  }

  private int getCacheTtlSeconds() {
    int ttl = configuration.cacheTtlSeconds();
    return ttl > 0 ? ttl : DEFAULT_CACHE_TTL_SECONDS;
  }

  private Set<TrustAnchor> getDefaultTrustAnchors() throws Exception {
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init((KeyStore) null);

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

  private String normalizeAaguid(String aaguid) {
    if (aaguid == null) {
      return "";
    }
    try {
      return UUID.fromString(aaguid).toString().toLowerCase();
    } catch (IllegalArgumentException e) {
      return aaguid.toLowerCase();
    }
  }

  public int getEntryCount() {
    ensureInitialized();
    return entryMap.size();
  }
}
