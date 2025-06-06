package org.idp.server.platform.uuid;

import org.idp.server.platform.exception.BadRequestException;

import java.util.UUID;

public interface UuidConvertable {

    default UUID convertUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID: " + uuid);
        }
    }
}
