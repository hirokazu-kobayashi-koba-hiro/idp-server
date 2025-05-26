module.exports = {
  presets: [],
  plugins: [
    [
      'redocusaurus',
      {
        specs: [
          {
            spec: 'openapi/swagger-ja.yaml',
            route: '/api/',
          },
          {
            spec: 'openapi/swagger-control-plane-ja.yaml',
            route: '/control-plane-api/',
          },
        ],
      },
    ],
  ],
};
