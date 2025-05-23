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

package org.idp.server.control_plane.management.onboarding.io;

import java.util.UUID;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationDescription;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationName;

public class OrganizationRegistrationRequest implements JsonReadable {

  String id;
  String name;
  String description;

  public OrganizationRegistrationRequest() {}

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Organization toOrganization() {
    String identifier = hasId() ? id : UUID.randomUUID().toString();
    OrganizationIdentifier organizationIdentifier = new OrganizationIdentifier(identifier);
    OrganizationName organizationName = new OrganizationName(name);
    OrganizationDescription organizationDescription = new OrganizationDescription(description);

    return new Organization(organizationIdentifier, organizationName, organizationDescription);
  }
}
