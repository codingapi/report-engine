import {defineConfig} from '@rsbuild/core';
import {pluginReact} from '@rsbuild/plugin-react';
import * as path from 'node:path';

// Docs: https://rsbuild.rs/config/
export default defineConfig({
    plugins: [pluginReact()],
    resolve: {
        alias: {
            "@/": path.resolve(__dirname, "src"),
        }
    },
    server: {
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8090',
                changeOrigin: true,
            },
        },
    },
});
