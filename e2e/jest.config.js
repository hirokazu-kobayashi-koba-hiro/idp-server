module.exports = {
  verbose: false,
  testMatch: ["**/*.test.js"],
  testEnvironment: "node",
  moduleDirectories: ["node_modules", "src"],
  maxWorkers: 5,
  setupFilesAfterEnv: [`${process.cwd()}/jest.setup.js`],
  testRunner: "jest-jasmine2",
};
