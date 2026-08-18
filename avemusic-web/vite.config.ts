import {
  defineConfig,
} from "vite";

import react
  from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    react(),
  ],

  server: {
    /*
     * 不再只监听回环地址，
     * 允许 Sunny / 局域网访问 Vite。
     */
    host: "0.0.0.0",

    port: 5173,

    strictPort: true,

    allowedHosts: [
      "music.3s.tunnelfrp.com",
    ],

    proxy: {
      "/api": {
        target:
            "http://127.0.0.1:8080",

        changeOrigin: true,
      },

      "/files": {
        target:
            "http://127.0.0.1:8090",

        changeOrigin: true,
      },

      "/upload": {
        target: "http://127.0.0.1:8090",
        changeOrigin: true,

        // 大音频上传给 10 分钟
        timeout: 10 * 60 * 1000,
        proxyTimeout: 10 * 60 * 1000,

        configure: (proxy) => {
          proxy.on("error", (error, req) => {
            console.error(
                "[upload proxy error]",
                req.url,
                error,
            );
          });
        },
      },
    },
  },
});

