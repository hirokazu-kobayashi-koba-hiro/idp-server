package org.idp.server.core.federation;

import org.idp.server.core.basic.json.JsonConverter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SsoStateCoder {

    private static final JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

    public static String encode(SsoState ssoState) {
        String json = jsonConverter.write(ssoState);

        return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static SsoState decode(String state) {
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        return jsonConverter.read(decoded, SsoState.class);
    }
}
