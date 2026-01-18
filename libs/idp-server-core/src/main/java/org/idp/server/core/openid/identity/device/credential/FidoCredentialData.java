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

package org.idp.server.core.openid.identity.device.credential;

import java.util.HashMap;
import java.util.Map;

/**
 * FIDO credential reference data.
 *
 * <p>Extracted from DeviceCredential.typeSpecificData() for FIDO2/UAF credentials. Contains
 * reference to the actual credential in a FIDO server (internal or external).
 */
public class FidoCredentialData {

  private final String fidoServerId;
  private final String credentialId;
  private final String rpId;
  private final String appId;

  private FidoCredentialData(String fidoServerId, String credentialId, String rpId, String appId) {
    this.fidoServerId = fidoServerId;
    this.credentialId = credentialId;
    this.rpId = rpId;
    this.appId = appId;
  }

  public static FidoCredentialData from(Map<String, Object> data) {
    String fidoServerId = (String) data.get("fido_server_id");
    String credentialId = (String) data.get("credential_id");
    String rpId = (String) data.get("rp_id");
    String appId = (String) data.get("app_id");
    return new FidoCredentialData(fidoServerId, credentialId, rpId, appId);
  }

  public static FidoCredentialData fido2(String fidoServerId, String credentialId, String rpId) {
    return new FidoCredentialData(fidoServerId, credentialId, rpId, null);
  }

  public static FidoCredentialData fidoUaf(String fidoServerId, String credentialId, String appId) {
    return new FidoCredentialData(fidoServerId, credentialId, null, appId);
  }

  public String fidoServerId() {
    return fidoServerId;
  }

  public boolean hasFidoServerId() {
    return fidoServerId != null && !fidoServerId.isEmpty();
  }

  public String credentialId() {
    return credentialId;
  }

  public boolean hasCredentialId() {
    return credentialId != null && !credentialId.isEmpty();
  }

  public String rpId() {
    return rpId;
  }

  public boolean hasRpId() {
    return rpId != null && !rpId.isEmpty();
  }

  public String appId() {
    return appId;
  }

  public boolean hasAppId() {
    return appId != null && !appId.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasFidoServerId()) map.put("fido_server_id", fidoServerId);
    if (hasCredentialId()) map.put("credential_id", credentialId);
    if (hasRpId()) map.put("rp_id", rpId);
    if (hasAppId()) map.put("app_id", appId);
    return map;
  }
}
