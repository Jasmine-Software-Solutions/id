#!/usr/bin/env node
// Bundles the three Lambda entry points with esbuild.
//
// The Lambda container image copies host-built `dist/` and `proto/` (the same
// "build on the host, COPY the artifact" flow the Gradle shadowJar Dockerfile
// uses), so this runs on the developer machine / CI, never inside the image.

import { build } from "esbuild";
import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

rmSync(path.join(root, "dist"), { recursive: true, force: true });

await build({
  absWorkingDir: root,
  entryPoints: {
    "handlers/security": "src/handlers/security.ts",
    "handlers/email": "src/handlers/email.ts",
    "handlers/witchwood": "src/handlers/witchwood.ts",
  },
  outdir: "dist",
  bundle: true,
  platform: "node",
  target: "node24",
  format: "cjs",
  sourcemap: "linked",
  keepNames: true,
  // @opentelemetry/otlp-grpc-exporter-base calls createRequire(import.meta.url)
  // to load @grpc/grpc-js lazily. The bundle is CommonJS, where esbuild leaves
  // import.meta.url undefined; point it at the bundle itself.
  define: {
    "import.meta.url": "__importMetaUrl",
  },
  banner: {
    js: 'const __importMetaUrl = require("node:url").pathToFileURL(__filename).href;',
  },
  // @grpc/proto-loader resolves .proto files relative to protoPath at runtime;
  // protobufjs is bundled successfully under esbuild (verified by the
  // integration tests), so no externals are needed. If a future version breaks,
  // add `external: ["@grpc/grpc-js", "@grpc/proto-loader"]` and ship
  // node_modules in the image instead.
  logLevel: "info",
});
