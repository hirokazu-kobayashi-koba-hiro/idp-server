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
import java.util.Base64;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.AesCryptoRuntimeException;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.HkdfSha256;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Encrypts one purpose's data under the tenant's data key.
 *
 * <p>Each message is encrypted under its own key, derived from the data key and a fresh random
 * salt. The salt is stored with the ciphertext. This keeps AES-GCM within its limit on random IVs
 * per key however much one data key encrypts, and means an IV that repeats by chance does not
 * repeat under the same key.
 *
 * <p>The tenant, the purpose, the data key id and the caller's {@link SealingContext} are
 * authenticated with every message. A ciphertext copied to another tenant's row, to a field of
 * another purpose, or to another owner within the tenant does not open.
 */
public class TenantDataCipher {

  static final int SALT_LENGTH = 32;
  static final int KEY_LENGTH = 32;

  DataKeyService dataKeyService;
  String purpose;
  SecureRandom random = new SecureRandom();

  public TenantDataCipher(DataKeyService dataKeyService, String purpose) {
    this.dataKeyService = dataKeyService;
    this.purpose = purpose;
  }

  /**
   * A sealer bound to the tenant's active data key. Use one sealer for every value written in one
   * row, so that the row's single key id opens all of them.
   */
  public Sealer sealer(Tenant tenant) {
    return new Sealer(tenant, dataKeyService.active(tenant, purpose));
  }

  public String open(Tenant tenant, String dataKeyId, SealedData data) {
    return open(tenant, dataKeyId, data, SealingContext.empty());
  }

  public String open(
      Tenant tenant, String dataKeyId, SealedData data, SealingContext sealingContext) {
    if (data.version() != SealedData.VERSION_1) {
      throw new AesCryptoRuntimeException("Unknown sealed data version: " + data.version());
    }
    DataKey dataKey = dataKeyService.key(tenant, purpose, dataKeyId);
    byte[] salt = Base64.getDecoder().decode(data.salt());
    return messageCipher(dataKey, salt)
        .decrypt(
            new EncryptedData(data.cipherText(), data.iv()),
            context(tenant, dataKeyId, sealingContext));
  }

  /**
   * Whether data under this key id is under an older generation than the tenant's active key, and
   * so should move to it.
   *
   * <p>Compared by generation, not by id. The active key is cached for up to a minute, so a server
   * that has not yet seen a rotation still takes the previous generation for the active one; going
   * by id, it would move data the new generation already holds back to the previous one.
   */
  public boolean isBehindActive(Tenant tenant, String dataKeyId) {
    DataKey active = dataKeyService.active(tenant, purpose);
    if (active.id().equals(dataKeyId)) {
      return false;
    }
    return dataKeyService.key(tenant, purpose, dataKeyId).version() < active.version();
  }

  private AesCipher messageCipher(DataKey dataKey, byte[] salt) {
    byte[] info = ("idp-server/" + purpose + "/message").getBytes(StandardCharsets.UTF_8);
    return new AesCipher(HkdfSha256.derive(dataKey.key(), salt, info, KEY_LENGTH));
  }

  private byte[] context(Tenant tenant, String dataKeyId, SealingContext sealingContext) {
    byte[] head =
        String.join("|", tenant.identifierValue(), purpose, dataKeyId, "")
            .getBytes(StandardCharsets.UTF_8);
    byte[] tail = sealingContext.bytes();
    byte[] context = new byte[head.length + tail.length];
    System.arraycopy(head, 0, context, 0, head.length);
    System.arraycopy(tail, 0, context, head.length, tail.length);
    return context;
  }

  public class Sealer {
    Tenant tenant;
    DataKey dataKey;

    Sealer(Tenant tenant, DataKey dataKey) {
      this.tenant = tenant;
      this.dataKey = dataKey;
    }

    /** The key id to store with everything this sealer seals. */
    public String keyId() {
      return dataKey.id();
    }

    public SealedData seal(String plainText) {
      return seal(plainText, SealingContext.empty());
    }

    /** Seals for one owner; opening must present the same context. */
    public SealedData seal(String plainText, SealingContext sealingContext) {
      byte[] salt = new byte[SALT_LENGTH];
      random.nextBytes(salt);
      EncryptedData encrypted =
          messageCipher(dataKey, salt)
              .encrypt(plainText, context(tenant, dataKey.id(), sealingContext));
      return new SealedData(
          SealedData.VERSION_1,
          Base64.getEncoder().encodeToString(salt),
          encrypted.iv(),
          encrypted.cipherText());
    }
  }
}
