/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantDomain;

public class TenantManagementUpdateContextCreator {
  Tenant adminTenant;
  Tenant before;
  TenantRequest request;
  User user;
  boolean dryRun;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TenantManagementUpdateContextCreator(
      Tenant adminTenant, Tenant before, TenantRequest request, User user, boolean dryRun) {
    this.adminTenant = adminTenant;
    this.before = before;
    this.request = request;
    this.user = user;
    this.dryRun = dryRun;
  }

  public TenantManagementUpdateContext create() {
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    String domain = jsonNodeWrapper.getValueOrEmptyAsString("domain");
    TenantAttributes attributes = extractAttributes();

    Tenant updatedDomain = before.updateDomain(new TenantDomain(domain));
    Tenant updateAttributes = updatedDomain.updateWithAttributes(attributes);

    return new TenantManagementUpdateContext(adminTenant, before, updateAttributes, user, dryRun);
  }

  public TenantAttributes extractAttributes() {
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    JsonNodeWrapper attributes = jsonNodeWrapper.getValueAsJsonNode("attributes");
    if (attributes == null) {
      return new TenantAttributes();
    }
    Map<String, Object> attributesMap = attributes.toMap();
    return new TenantAttributes(attributesMap);
  }
}
