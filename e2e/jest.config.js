module.exports = {
  verbose: false,
  testMatch: ["**/*.test.js"],
  testEnvironment: "node",
  moduleDirectories: ["node_modules", "src"],
  maxWorkers: 1,
  setupFilesAfterEnv: [`${process.cwd()}/jest.setup.js`],
  testRunner: "jest-jasmine2",
  reporters: [
    "default",
    [
      "./node_modules/jest-html-reporters",
      {
        hideIcon: true,
        pageTitle: "E2E Test Report",
        publicPath: "./test-result",
        filename: "index.html",
        expand: true,
      },
    ],
  ],
};
