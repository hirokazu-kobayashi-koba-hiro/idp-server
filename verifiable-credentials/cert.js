import { issueTransaction } from "./ethereum.js";

import crypto from "crypto";
import { Encoder } from "@vaultie/lds-merkle-proof-2019";
import MerkleTools from "merkle-tools";
import jsonld from "jsonld";

class MerkleTreeGenerator {
  constructor(normalizedData) {
    this.tree = new MerkleTools({ hashType: "sha256" });
    const hashed = this.hashByteArray(normalizedData);
    console.log(hashed);
    this.tree.addLeaf(hashed);
    this.tree.makeTree();
  }

  hashByteArray(data) {
    const hashed = crypto.createHash("sha256").update(data).digest("hex");
    return hashed;
  }

  ensureString(value) {
    if (typeof value === "string") {
      return value;
    }
    return value.toString("utf-8");
  }

  getBlockchainData() {
    this.tree.makeTree();
    const merkleRoot = this.tree.getMerkleRoot().toString("hex");
    return Buffer.from(this.ensureString(merkleRoot), "hex");
  }

  generateProof(transactionId, verificationMethod, chain) {
    const root = this.ensureString(this.tree.getMerkleRoot().toString("hex"));

    const proof = this.tree.getProof(0);
    const proof2 = proof.map((p) => {
      const dict2 = {};
      for (const [key, value] of Object.entries(p)) {
        dict2[key] = this.ensureString(value);
      }
      return dict2;
    });

    const targetHash = this.tree.getLeaf(0).toString("hex");
    const merkleJson = {
      path: proof2,
      merkleRoot: root,
      targetHash: targetHash,
      anchors: [this.toBlink({ chain, transactionId })],
    };

    const proofValue = new Encoder(merkleJson).encode();
    const merkleProof = {
      type: "MerkleProof2019",
      created: new Date().toISOString(),
      proofValue: proofValue.toString("utf8"),
      proofPurpose: "assertionMethod",
      verificationMethod: verificationMethod,
    };

    console.log("merkleProof:", JSON.stringify(merkleProof, null, 2));

    return merkleProof;
  }

  toBlink = ({ chain, transactionId }) => {
    let blink = "blink:";

    switch (chain) {
      case "bitcoin_regtest":
        blink += "btc:regtest:";
        break;
      case "bitcoin_testnet":
        blink += "btc:testnet:";
        break;
      case "bitcoin_mainnet":
        blink += "btc:mainnet:";
        break;
      case "ethereum_ropsten":
        blink += "eth:ropsten:";
        break;
      case "ethereum_goerli":
        blink += "eth:goerli:";
        break;
      case "ethereum_sepolia":
        blink += "eth:sepolia:";
        break;
      case "ethereum_mainnet":
        blink += "eth:mainnet:";
        break;
      case "mockchain":
        blink += "mocknet:";
        break;
      default:
        throw new Error("UnknownChainError: " + chain);
    }

    return blink + transactionId;
  };
}

export const issueBlockCert = async ({
  address,
  privateKey,
  verificationMethod,
  chain,
  credential,
}) => {
  console.log("start");
  const normalizedCredential = await normalizeWithJsonld(credential);
  console.log(normalizedCredential);
  const merkleTreeGenerator = new MerkleTreeGenerator(normalizedCredential);
  const blockchainData = merkleTreeGenerator.getBlockchainData();
  console.log("Blockchain Data:", blockchainData);
  const { payload, error } = await issueTransaction({
    address,
    privateKey,
    data: blockchainData,
  });
  if (error) {
    console.log("error");
    return {
      error,
    };
  }
  console.log("verificationMethod", verificationMethod);
  const merkleProof = merkleTreeGenerator.generateProof(
    payload.transactionId,
    verificationMethod,
    chain,
  );
  const vc = {
    ...credential,
    proof: merkleProof,
  };
  console.log(vc);
  return {
    payload: vc,
  };
};

const normalizeWithJsonld = async (value) => {
  const jsonldHandler = jsonld();
  const normalizedValue = await jsonldHandler.normalize(value);
  return normalizedValue.toString("utf-8");
};
