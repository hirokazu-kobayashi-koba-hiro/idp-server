module.exports = {
  verbose: false,
  testMatch: ["**/*.test.js"],
  testEnvironment: "allure-jest/node",
  testEnvironmentOptions: {
    resultsDir: "./allure-results",
    environmentInfo: {
      project: "idp-server",
      test_type: "E2E",
    },
  },
  moduleDirectories: ["node_modules", "src"],
  maxWorkers: 5,
  setupFilesAfterEnv: [`${process.cwd()}/jest.setup.js`],
  // jest-circus (Jest 27+ default) is required by allure-jest
  // Removed: testRunner: "jest-jasmine2"
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
