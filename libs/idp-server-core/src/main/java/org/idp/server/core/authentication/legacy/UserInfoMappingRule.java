package org.idp.server.core.authentication.legacy;

import org.idp.server.core.basic.json.JsonReadable;

public class UserInfoMappingRule implements JsonReadable {
    String from;
    String to;
    String type;

    public UserInfoMappingRule() {}

    public UserInfoMappingRule(String from, String to, String type) {
        this.from = from;
        this.to = to;
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getType() {
        return type;
    }
}
