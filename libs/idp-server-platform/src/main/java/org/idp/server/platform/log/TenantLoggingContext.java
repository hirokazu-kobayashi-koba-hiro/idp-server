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

package org.idp.server.platform.log;

import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.slf4j.MDC;

public class TenantLoggingContext {

  private static final String TENANT_ID_KEY = "tenant_id";
  private static final String CLIENT_ID_KEY = "client_id";
  private static final String USER_ID_KEY = "user_id";
  private static final String USER_EX_SUB_KEY = "user_ex_sub";

  public static void setTenant(TenantIdentifier tenantIdentifier) {
    if (Objects.nonNull(tenantIdentifier) && tenantIdentifier.exists()) {
      MDC.put(TENANT_ID_KEY, tenantIdentifier.value());
    }
  }

  public static String getCurrentTenant() {
    return MDC.get(TENANT_ID_KEY);
  }

  public static boolean hasTenant() {
    return Objects.nonNull(MDC.get(TENANT_ID_KEY));
  }

  public static void setClientId(String clientId) {
    if (Objects.nonNull(clientId) && !clientId.isEmpty()) {
      MDC.put(CLIENT_ID_KEY, clientId);
    }
  }

  public static String getCurrentClientId() {
    return MDC.get(CLIENT_ID_KEY);
  }

  public static boolean hasClientId() {
    return Objects.nonNull(MDC.get(CLIENT_ID_KEY));
  }

  public static void setUserId(String userId) {
    if (Objects.nonNull(userId) && !userId.isEmpty()) {
      MDC.put(USER_ID_KEY, userId);
    }
  }

  public static void setUserExSub(String userExSub) {
    if (Objects.nonNull(userExSub) && !userExSub.isEmpty()) {
      MDC.put(USER_EX_SUB_KEY, userExSub);
    }
  }

  public static String getCurrentUserId() {
    return MDC.get(USER_ID_KEY);
  }

  public static boolean hasUserId() {
    return Objects.nonNull(MDC.get(USER_ID_KEY));
  }

  public static void clearTenant() {
    MDC.remove(TENANT_ID_KEY);
  }

  public static void clearClientId() {
    MDC.remove(CLIENT_ID_KEY);
  }

  public static void clearUserId() {
    MDC.remove(USER_ID_KEY);
  }

  public static void clearAll() {
    MDC.clear();
  }

  public static String getTenantIdKey() {
    return TENANT_ID_KEY;
  }

  public static String getClientIdKey() {
    return CLIENT_ID_KEY;
  }

  public static String getUserIdKey() {
    return USER_ID_KEY;
  }
}
