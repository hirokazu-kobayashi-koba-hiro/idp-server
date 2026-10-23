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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.crypto.AesCryptoRuntimeException;
import org.idp.server.platform.crypto.EncryptionKeyring;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

class TenantDataCipherTest {

  static final String PURPOSE = "account-linking-token";
  static final String K1 = key(1);
  static final String K2 = key(2);

  static String key(int fill) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, (byte) fill);
    return Base64.getEncoder().encodeToString(bytes);
  }

  static Tenant tenant(String id) {
    return new Tenant() {
      @Override
      public TenantIdentifier identifier() {
        return new TenantIdentifier(id);
      }

      @Override
      public String identifierValue() {
        return id;
      }
    };
  }

  static final Tenant A = tenant("11111111-1111-1111-1111-111111111111");
  static final Tenant B = tenant("22222222-2222-2222-2222-222222222222");

  /** One table shared by every "server", as the database would be. */
  static class Store implements TenantDataKeyRepository, TenantDataKeyOperationRepository {
    Map<String, TenantDataKey> rows = new LinkedHashMap<>();

    @Override
    public TenantDataKey findActive(Tenant tenant, String purpose) {
      return rows.values().stream()
          .filter(k -> k.tenantIdentifier().value().equals(tenant.identifierValue()))
          .filter(k -> k.purpose().equals(purpose) && k.status() == DataKeyStatus.ACTIVE)
          .findFirst()
          .orElse(new TenantDataKey());
    }

    @Override
    public TenantDataKey find(Tenant tenant, String id) {
      TenantDataKey key = rows.get(id);
      if (key == null || !key.tenantIdentifier().value().equals(tenant.identifierValue())) {
        return new TenantDataKey();
      }
      return key;
    }

    @Override
    public int latestVersion(Tenant tenant, String purpose) {
      return rows.values().stream()
          .filter(k -> k.tenantIdentifier().value().equals(tenant.identifierValue()))
          .filter(k -> k.purpose().equals(purpose))
          .mapToInt(TenantDataKey::version)
          .max()
          .orElse(0);
    }

    @Override
    public boolean registerIfAbsent(Tenant tenant, TenantDataKey key) {
      if (findActive(tenant, key.purpose()).exists()) {
        return false;
      }
      rows.put(key.id(), key);
      return true;
    }

    @Override
    public void updateStatus(Tenant tenant, String id, DataKeyStatus status) {
      TenantDataKey k = rows.get(id);
      rows.put(
          id,
          new TenantDataKey(
              k.id(),
              k.tenantIdentifier(),
              k.purpose(),
              k.version(),
              status,
              k.wrappedKey(),
              k.kekId(),
              k.createdAt()));
    }

    @Override
    public List<TenantDataKey> findWrappedByOtherThan(Tenant tenant, String kekId, int limit) {
      List<TenantDataKey> result = new ArrayList<>();
      for (TenantDataKey k : rows.values()) {
        if (!k.kekId().equals(kekId) && result.size() < limit) result.add(k);
      }
      return result;
    }

    @Override
    public void updateWrapping(Tenant tenant, TenantDataKey key) {
      rows.put(key.id(), key);
    }

    @Override
    public Map<String, Long> countByKekId(Tenant tenant) {
      Map<String, Long> counts = new HashMap<>();
      rows.values().forEach(k -> counts.merge(k.kekId(), 1L, Long::sum));
      return counts;
    }
  }

  static TenantDataCipher server(Store store, String keys, String active) {
    return new TenantDataCipher(
        new DataKeyService(store, store, EncryptionKeyring.of(keys, active, null)), PURPOSE);
  }

  static DataKeyService service(Store store, String keys, String active) {
    return new DataKeyService(store, store, EncryptionKeyring.of(keys, active, null));
  }

  @Test
  void sealsAndOpensUnderTheTenantsKey() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer sealer = cipher.sealer(A);
    SealedData sealed = sealer.seal("token");

    assertEquals("token", cipher.open(A, sealer.keyId(), sealed));
    assertEquals(1, store.rows.size());
    // A second seal of the same text differs: a fresh salt and key each time.
    SealedData again = cipher.sealer(A).seal("token");
    assertNotEquals(sealed.cipherText(), again.cipherText());
    assertNotEquals(sealed.salt(), again.salt());
  }

  @Test
  void eachTenantGetsItsOwnKey() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer a = cipher.sealer(A);
    TenantDataCipher.Sealer b = cipher.sealer(B);
    assertNotEquals(a.keyId(), b.keyId());

    SealedData sealedForA = a.seal("token");
    // Copied to tenant B's row with B's key id: B's key does not open it.
    assertThrows(AesCryptoRuntimeException.class, () -> cipher.open(B, b.keyId(), sealedForA));
    // Nor with A's key id read as B: B cannot see A's key at all.
    assertThrows(AesCryptoRuntimeException.class, () -> cipher.open(B, a.keyId(), sealedForA));
  }

  @Test
  void tamperedSaltDoesNotOpen() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer sealer = cipher.sealer(A);
    SealedData sealed = sealer.seal("token");
    SealedData tampered =
        new SealedData(
            sealed.version(),
            Base64.getEncoder().encodeToString(new byte[32]),
            sealed.iv(),
            sealed.cipherText());
    assertThrows(AesCryptoRuntimeException.class, () -> cipher.open(A, sealer.keyId(), tampered));
  }

  @Test
  void rotatingTheKekRewrapsDataKeysWithoutTouchingData() {
    Store store = new Store();
    TenantDataCipher before = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer sealer = before.sealer(A);
    SealedData sealed = sealer.seal("token");
    String dataKeyId = sealer.keyId();

    // k2 active; k1 still held. The data key is still wrapped by k1 and opens.
    DataKeyService rotating = service(store, "k2:" + K2 + ",k1:" + K1, "k2");
    assertEquals(1, rotating.report(A).pendingRewrap());
    assertEquals(1, rotating.rewrap(A, 100));
    assertEquals(0, rotating.report(A).pendingRewrap());

    // k1 removed. The same ciphertext, never rewritten, opens under the re-wrapped data key.
    TenantDataCipher after = server(store, "k2:" + K2, "k2");
    assertEquals("token", after.open(A, dataKeyId, sealed));
    assertEquals(0, after.dataKeyService.report(A).unopenable());
  }

  @Test
  void removingAKekTooEarlyIsReported() {
    Store store = new Store();
    server(store, "k1:" + K1, "k1").sealer(A).seal("token");
    DataKeyService withoutK1 = service(store, "k2:" + K2, "k2");
    assertEquals(1, withoutK1.report(A).unopenable());
  }

  @Test
  void newGenerationKeepsOlderDataReadable() {
    Store store = new Store();
    DataKeyService service = service(store, "k1:" + K1, "k1");
    TenantDataCipher cipher = new TenantDataCipher(service, PURPOSE);
    TenantDataCipher.Sealer first = cipher.sealer(A);
    SealedData old = first.seal("old");

    String next = service.rotate(A, PURPOSE);
    assertNotEquals(first.keyId(), next);
    assertFalse(cipher.isCurrent(A, first.keyId()));
    assertEquals(next, cipher.sealer(A).keyId());
    assertEquals("old", cipher.open(A, first.keyId(), old));
  }

  @Test
  void destroyedKeyOpensNothing() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer sealer = cipher.sealer(A);
    SealedData sealed = sealer.seal("token");
    store.updateStatus(A, sealer.keyId(), DataKeyStatus.DESTROYED);

    TenantDataCipher fresh = server(store, "k1:" + K1, "k1");
    assertThrows(AesCryptoRuntimeException.class, () -> fresh.open(A, sealer.keyId(), sealed));
  }

  @Test
  void aWrappedKeyCopiedToAnotherRowDoesNotOpen() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    String aKey = cipher.sealer(A).keyId();
    String bKey = cipher.sealer(B).keyId();
    TenantDataKey a = store.rows.get(aKey);
    TenantDataKey b = store.rows.get(bKey);
    // Put A's wrapped key into B's row.
    store.rows.put(bKey, b.rewrapped(a.wrappedKey(), a.kekId()));

    TenantDataCipher fresh = server(store, "k1:" + K1, "k1");
    assertThrows(
        AesCryptoRuntimeException.class, () -> fresh.open(B, bKey, fresh.sealer(B).seal("x")));
  }

  @Test
  void unknownVersionIsRefused() {
    Store store = new Store();
    TenantDataCipher cipher = server(store, "k1:" + K1, "k1");
    TenantDataCipher.Sealer sealer = cipher.sealer(A);
    SealedData sealed = sealer.seal("token");
    SealedData future = new SealedData(2, sealed.salt(), sealed.iv(), sealed.cipherText());
    assertThrows(AesCryptoRuntimeException.class, () -> cipher.open(A, sealer.keyId(), future));
  }
}
