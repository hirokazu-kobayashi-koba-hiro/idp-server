import Web3 from "web3";

const web3 = new Web3("wss://eth-sepolia.g.alchemy.com/v2/zO8Ii3ROEXb89VatiAmTWkmaGMJHY4hA");

export const getBalance = async ({ address }) => {
  const balance = await web3.eth.getBalance(address);
  console.log("getBalance: ", balance);
  return balance;
};

export const broadcastTx = async ({ tx }) => {
  const response = await web3.eth.sendSignedTransaction(tx).hex();
  console.info("Broadcasting transaction with EthereumRPCProvider: ", response);
  return response;
};

export const getTransactionCount = async ({ sender }) => {
  const nonce = await web3.eth.getTransactionCount(sender);
  console.info("getTransactionCount nonce: ", nonce);
  return nonce;
};


export const signTransaction = async ({ transaction, privateKey }) => {
  const hexedPrivateKey = toHex(privateKey);
  const signedTransaction = await web3.eth.accounts.signTransaction(transaction, hexedPrivateKey);
  console.log(signedTransaction);
  return signedTransaction;
};

const toHex = (value) => {
  const hexValue = web3.utils.toHex(value)
  console.log(hexValue)
  return hexValue;
}
getBalance({ address: "0x260841F2440ffd1884F7eeAC551b892dD4434073" });
signTransaction({
  transaction: {
    from: "0x260841F2440ffd1884F7eeAC551b892dD4434073",
    to: "0xF0109fC8DF283027b6285cc889F5aA624EaC1F55",
    value: "1000000000",
    gas: 2000000,
    gasPrice: "234567897654321",
    nonce: 0,
  },
  privateKey: "",
});
