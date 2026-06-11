import {defineConfig} from '@rslib/core';
import { pluginReact } from '@rsbuild/plugin-react';

import * as path from 'node:path';

export default defineConfig({
    source: {
        entry: {
            index: ['./src/**'],
        },
    },
    lib: [
        {
            bundle: false,
            dts: true,
            format: 'esm',
        },
    ],
    resolve: {
        alias: {
            "@/": path.resolve(__dirname, "src"),
        }
    },
    output: {
        target: 'web',
    },
    plugins: [
        pluginReact()
    ]
});
