#!/bin/zsh
# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $AUTHORIZATION_SERVER_URL"

mkdir -p ./config/generated/${ENV}/admin-tenant

# tenant
export TENANT_NAME=admin-tenant
export ALLOW_ORIGINS='"http://localhost:3000","http://localhost:3100","http://localhost:5173","https://idp-auth-frontend-ecru.vercel.app","https://idp-auth-frontend-diqt.vercel.app"'

# authorization-server
export SERVER_JWKS='{ "keys": [ { "kty": "EC", "d": "yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ", "use": "sig", "crv": "P-256", "kid": "access_token", "x": "iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0", "y": "rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ", "alg": "ES256" } ,{ "kty": "EC", "d": "HrgT4zqM2BvrlwUWagyeNnZ40nZ7rTY4gYG9k99oGJg", "use": "enc", "crv": "P-256", "kid": "request_enc_key", "x": "PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo", "y": "wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4", "alg": "ECDH-ES" },{ "p": "3yO0t6JmIHnsW6SrAfHa_4Ynd3unyrTQnCpPCo1GAlzbIRUgyDWohcfKaQ9rFoeO67b91dcpDiH6jTuxdD1S95ph7KB2RuDTGhhD5jg5_VdEthnlK7Hw3GZhFRnsBAKxTmJuJ3R12tcm33054vEtxAZB3FWyVsf0WI36vbDYRU0", "kty": "RSA", "q": "n10rboIDPMk0Qyvug--W_s4yB1j7QdIvduBO-bZgrG_rLLXgFTzKbef_ArygXBlCqinwn7MSw6O5G6IKw5raK7IvehlDT_xC8mOtajiFcj_t9PkaUdHXNR-tbJdLJwohbJNwXvP28DhMCTUPLTxW005sVFfChbtFiQ22eJFsrF0", "d": "azIA2jVLHvXr2jM0Eq9rQCErJqjNjtDu86k0-kmF3xfjuAS9IBuGnU9W0bkuaOaY85ngHEb0qf-nXRnOaF_6s7glSVm0mDM6mNgDOzNmgqHV14mJTkmixAmxrGmvVOix06mIUc2liG_wUO7OHVkd9kgNV5QVj-EF-VDULTin6FfNcJgcUsYAb59_MHASvS8foWNb3o9bSdPFLDDaT0mzl2nwEe8SERtIsLBrcbbKcedQHxm1SUq8sks-1L6E8Qfx6jFQJJMNLj-XBbwrFxww1DwrhmNz_1AOZM5VDTZg-kMDqjcBvgjHzmA2o9X9p1Gaa5xadO51msiZQAbPSMK1sQ", "e": "AQAB", "use": "sig", "kid": "id_token_nextauth", "qi": "A3s2bGrlaC6WU-vQGvugen8vx4ouqrTY60ZD-E1YRp6ADbC7g6318WZ7IZl-FoFGto--NvnMFOsNYkSMXrFcaroixNjJEo6HcNWs7BTqS7PICZmtmUWr5V2f9RENwJxbG9GnYDsZTQYM84j2PNsJxAzjmFQvQzygsfeEyuzAtJg", "dp": "vUWmNtWz1vxUdm-49k9WOcRrmbfz3cd949knboXiyoJFBUzMn8aUCdYsZO1FIrkdi-eObGKzWl-MDVyC61xREeGMCpEZgomVxt6qSY-L8M6jY-uXLncjHXBiDOoN_mDiUODBGwp4JYa2XH_2J__3l_zOxLyUJ3Q4WR0lgN2OtUk", "alg": "RS256", "dq": "g1bKAJ1uBZ7dT67ZOCsxinZtjNis2qZbL-HVtL-2FOd4LrUGJPqg6suUw7CpiL3Yz10ZTsTK5in82OVHccYhoHmN31cKvtTsZ8_2j-BdOretaYQTSPNkJgghaamW6mnS-iTZK6htD7WWFNCB3YopFKVBapGZY5XfzQBcLinMIpE", "n": "iuhjEgaY4KCS_rbvODf-QadNvj9DaoHx8PzPKpZdxx_g0aQx7wvachzc7A_F8RKkToM10qGrDtFFehTCzxcC44WHsFezRd3yxNNhdfVEfcHApLpMYaq8A3HAi8NMN-cMkfqQRIvsvDmbYtt8B6EZG8YsFhjMZRY-7gzF_LGdIMoh3Af0WUx-L_AWRIawXuAwDIm11OQh9bt3hdoJbZFd9B4Wf1H5oxbsJ5MZQAQ9ltc23F60zqoDt5RAehC30w7rMeYH8WKoKpS5-odhVUDqieAS5j8iVegjcl63CoxS2BRLmN9UYzQvuEo-HUeWbucBlXmqD4sdn6Ypyt5QZpzo-Q" }] }'
export SERVER_JWKS_ESCAPED=$(echo "$SERVER_JWKS" | sed 's/"/\\"/g')
export TOKEN_SIGNED_KEY=id_token_nextauth
export ID_TOKEN_SIGNED_KEY=id_token_nextauth

# admin-client
export ADMIN_CLIENT_REDIRECT_URIS='"http://localhost:8081/callback","https://www.certification.openid.net/test/a/idp_oidc_basic/callback","https://localhost.emobix.co.uk:8443/test/a/idp_oidc_basic/callback","https://localhost.emobix.co.uk:8443/test/a/idp_oidc_implicit/callback","https://localhost.emobix.co.uk:8443/test/a/idp_oidc_hybrid/callback"'
export ADMIN_CLIENT_JWKS='{ "keys": [{ "kty": "EC", "d": "uj7jNVQIfSCBdiV4A_yVnY8htLZS7nskIXAGIVDb9oM", "use": "sig", "crv": "P-256", "kid": "request_secret_post", "x": "H4E6D5GqxTrZshUvkG-z0sAWNkbixERVSpm3YjcIU1U", "y": "413NbE2n5PeQJlG1Nfq_nCbqR_ZKbVAzsyyrmYph7Fs", "alg": "ES256" }] }'
export ADMIN_CLIENT_JWKS_ESCAPED=$(echo "$ADMIN_CLIENT_JWKS" | sed 's/"/\\"/g')

envsubst < ./config/templates/admin/initial.json > ./config/generated/${ENV}/admin-tenant/initial.json