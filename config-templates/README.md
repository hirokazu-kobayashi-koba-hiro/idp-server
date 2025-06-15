
```shell
uuidgen | tr 'A-Z' 'a-z'
for i in $(seq 1 4); do uuidgen | tr 'A-Z' 'a-z'; done
```

```shell
./config-templates/config-upsert.sh \
-e "local" \
-u ito.ichiro@gmail.com \
-p successUserCode \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-b http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-n aa983fa6-8000-480e-ae7a-bddb12201d84 \
-l beae8481-ad2b-4a7c-969e-1ed90e5defe8 \
-d false
```