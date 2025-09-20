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

  private static final String TENANT_ID_KEY = "tenantId";

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

  public static void clear() {
    MDC.remove(TENANT_ID_KEY);
  }

  public static void clearAll() {
    MDC.clear();
  }

  public static String getTenantIdKey() {
    return TENANT_ID_KEY;
  }
}
