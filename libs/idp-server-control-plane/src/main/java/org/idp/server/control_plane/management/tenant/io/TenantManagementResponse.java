/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.io;

import java.util.Map;

public class TenantManagementResponse {

  TenantManagementStatus status;
  Map<String, Object> contents;

  public TenantManagementResponse(TenantManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public TenantManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isOk() {
    return this.status.isOk();
  }
}
