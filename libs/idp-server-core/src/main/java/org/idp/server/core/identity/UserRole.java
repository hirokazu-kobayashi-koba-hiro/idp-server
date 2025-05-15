package org.idp.server.core.identity;

import org.idp.server.basic.json.JsonReadable;

import java.io.Serializable;

public class UserRole implements Serializable, JsonReadable {
    String roleId;
    String roleName;

    public UserRole() {}

    public UserRole(String roleId, String roleName) {
        this.roleId = roleId;
        this.roleName = roleName;
    }

    public String roleId() {
        return roleId;
    }

    public String roleName() {
        return roleName;
    }
}
