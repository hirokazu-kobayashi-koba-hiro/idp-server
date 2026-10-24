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
package org.idp.server.platform.crypto.datakey;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.platform.crypto.AesCryptoRuntimeException;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.EncryptionKeyring;
import org.idp.server.platform.crypto.KeyedAesCipher;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Tenant data keys: created on first use, unwrapped with the deployment's key encryption keys, and
 * kept in memory.
 *
 * <p>A key encryption key (KEK) only ever wraps data keys. Rotating it re-wraps the rows of {@code
 * tenant_data_keys}; the data each data key encrypted is not touched.
 *
 * <p>Only keys read back from the database are cached. A key created in this request is not, so a
 * rolled-back creation cannot leave the cache pointing at a key that was never stored.
 */
public class DataKeyService {

  static final String WRAP_PURPOSE = "data-key-wrap";
  static final int KEY_LENGTH = 32;
  static final Duration ACTIVE_CACHE_TTL = Duration.ofSeconds(60);
  static final Duration KEY_CACHE_TTL = Duration.ofMinutes(5);

  TenantDataKeyRepository repository;
  TenantDataKeyOperationRepository operationRepository;
  EncryptionKeyring keyring;
  KeyedAesCipher kekCipher;
  SecureRandom random = new SecureRandom();
  Map<String, Cached> keys = new ConcurrentHashMap<>();
  Map<String, Cached> actives = new ConcurrentHashMap<>();
  LoggerWrapper log = LoggerWrapper.getLogger(DataKeyService.class);

  public DataKeyService(
      TenantDataKeyRepository repository,
      TenantDataKeyOperationRepository operationRepository,
      EncryptionKeyring keyring) {
    this.repository = repository;
    this.operationRepository = operationRepository;
    this.keyring = keyring;
    this.kekCipher = keyring.cipher(WRAP_PURPOSE);
  }

  /** The key new data of this purpose is encrypted under, created if the tenant has none. */
  public DataKey active(Tenant tenant, String purpose) {
    String cacheKey = tenant.identifierValue() + "|" + purpose;
    Cached cached = actives.get(cacheKey);
    if (cached != null && cached.isFresh()) {
      return cached.dataKey;
    }

    TenantDataKey stored = repository.findActive(tenant, purpose);
    if (!stored.exists()) {
      NewKey created = newKey(tenant, purpose, repository.latestVersion(tenant, purpose) + 1);
      if (repository.registerIfAbsent(tenant, created.stored)) {
        log.info(
            "Tenant data key created. tenant={}, purpose={}, version={}, kek={}",
            tenant.identifierValue(),
            purpose,
            created.stored.version(),
            created.stored.kekId());
        return new DataKey(created.stored.id(), created.material);
      }
      stored = repository.findActiveCommitted(tenant, purpose);
      if (!stored.exists()) {
        throw new AesCryptoRuntimeException(
            "No active data key after a concurrent creation: " + purpose);
      }
    }

    DataKey dataKey = new DataKey(stored.id(), unwrap(stored));
    actives.put(cacheKey, new Cached(dataKey, ACTIVE_CACHE_TTL));
    keys.put(stored.id(), new Cached(dataKey, KEY_CACHE_TTL));
    return dataKey;
  }

  /** The key with this id, for opening data it encrypted. */
  public DataKey key(Tenant tenant, String purpose, String id) {
    if (id == null || id.isEmpty()) {
      throw new AesCryptoRuntimeException("Data key id is missing");
    }
    Cached cached = keys.get(id);
    if (cached != null && cached.isFresh()) {
      return cached.dataKey;
    }

    TenantDataKey stored = repository.find(tenant, id);
    if (!stored.exists() || !stored.purpose().equals(purpose)) {
      throw new AesCryptoRuntimeException("Data key not found: " + id);
    }
    if (stored.status() == DataKeyStatus.DESTROYED) {
      throw new AesCryptoRuntimeException("Data key is destroyed: " + id);
    }

    DataKey dataKey = new DataKey(stored.id(), unwrap(stored));
    keys.put(stored.id(), new Cached(dataKey, KEY_CACHE_TTL));
    return dataKey;
  }

  /**
   * Starts a new generation: new data is encrypted under a new key, and the current one stays to
   * open what it already encrypted.
   *
   * <p>Nothing is re-encrypted here. Other servers keep using the previous key for up to the
   * active-key cache lifetime, which is harmless because it still opens.
   *
   * @return the new key's id
   */
  public String rotate(Tenant tenant, String purpose) {
    TenantDataKey current = repository.findActive(tenant, purpose);
    if (current.exists()) {
      repository.updateStatus(tenant, current.id(), DataKeyStatus.PASSIVE);
    }
    NewKey created = newKey(tenant, purpose, repository.latestVersion(tenant, purpose) + 1);
    if (!repository.registerIfAbsent(tenant, created.stored)) {
      throw new AesCryptoRuntimeException("Data key rotation raced another rotation: " + purpose);
    }
    actives.remove(tenant.identifierValue() + "|" + purpose);
    log.info(
        "Tenant data key rotated. tenant={}, purpose={}, version={}",
        tenant.identifierValue(),
        purpose,
        created.stored.version());
    return created.stored.id();
  }

  /**
   * Re-wraps data keys still wrapped by a key encryption key other than the active one.
   *
   * @param tenant the administrator's tenant the operation runs under
   * @param limit the most keys re-wrapped in one call
   * @return how many were re-wrapped
   */
  public int rewrap(Tenant tenant, int limit) {
    String activeKekId = kekCipher.activeKeyId();
    List<TenantDataKey> stale =
        operationRepository.findWrappedByOtherThan(tenant, activeKekId, limit);
    for (TenantDataKey key : stale) {
      byte[] material = unwrap(key);
      operationRepository.updateWrapping(tenant, key.rewrapped(wrap(key, material), activeKekId));
    }
    return stale.size();
  }

  /** Every generation of the tenant's keys. */
  public List<TenantDataKey> keys(Tenant tenant) {
    return repository.findList(tenant);
  }

  /**
   * Data keys across tenants.
   *
   * @param tenant the administrator's tenant the operation runs under
   */
  public List<TenantDataKey> findList(Tenant tenant, TenantDataKeyQueries queries) {
    return operationRepository.findList(tenant, queries);
  }

  /**
   * @param tenant the administrator's tenant the operation runs under
   */
  public long findTotalCount(Tenant tenant, TenantDataKeyQueries queries) {
    return operationRepository.findTotalCount(tenant, queries);
  }

  /** Whether this server holds the key encryption key, and so can open what it wraps. */
  public boolean holds(String kekId) {
    return keyring.keyIds().contains(kekId);
  }

  /** Data keys per key encryption key, and which key encryption keys this server holds. */
  public DataKeyStatusReport report(Tenant tenant) {
    Map<String, Long> counts = new LinkedHashMap<>(operationRepository.countByKekId(tenant));
    List<String> configured = new ArrayList<>(keyring.keyIds());
    return new DataKeyStatusReport(kekCipher.activeKeyId(), configured, counts);
  }

  private NewKey newKey(Tenant tenant, String purpose, int version) {
    byte[] material = new byte[KEY_LENGTH];
    random.nextBytes(material);
    TenantDataKey unwrapped =
        new TenantDataKey(
            UUID.randomUUID().toString(),
            tenant.identifier(),
            purpose,
            version,
            DataKeyStatus.ACTIVE,
            null,
            kekCipher.activeKeyId(),
            SystemDateTime.now());
    return new NewKey(
        unwrapped.rewrapped(wrap(unwrapped, material), kekCipher.activeKeyId()), material);
  }

  private EncryptedData wrap(TenantDataKey key, byte[] material) {
    return kekCipher.encrypt(Base64.getEncoder().encodeToString(material), wrappingContext(key));
  }

  private byte[] unwrap(TenantDataKey key) {
    String encoded = kekCipher.decrypt(key.kekId(), key.wrappedKey(), wrappingContext(key));
    return Base64.getDecoder().decode(encoded);
  }

  /** Binds a wrapped key to its own row, so it does not open when copied to another. */
  static byte[] wrappingContext(TenantDataKey key) {
    return String.join("|", key.tenantIdentifier().value(), key.purpose(), key.id())
        .getBytes(StandardCharsets.UTF_8);
  }

  /** A key just generated: what is stored, and the plain key, which is not. */
  record NewKey(TenantDataKey stored, byte[] material) {}

  static class Cached {
    DataKey dataKey;
    Instant expiresAt;

    Cached(DataKey dataKey, Duration ttl) {
      this.dataKey = dataKey;
      this.expiresAt = Instant.now().plus(ttl);
    }

    boolean isFresh() {
      return Instant.now().isBefore(expiresAt);
    }
  }
}
