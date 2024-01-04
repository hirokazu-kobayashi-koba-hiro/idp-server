import fs from "fs";
import keyto from "@trust/keyto";

const main = () => {
  const file = process.argv[2];
  const blk = fs.readFileSync(file, "utf-8");
  const key = keyto.from(blk, "blk");
  const jwk = key.toJwk("public");

  console.log(JSON.stringify(jwk, null, 2));
};

main();
