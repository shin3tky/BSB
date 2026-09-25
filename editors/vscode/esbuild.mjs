import * as esbuild from "esbuild";
import { rm } from "node:fs/promises";

const production = process.argv.includes("--production");
const watch = process.argv.includes("--watch");

if (production) {
  await rm("dist", { recursive: true, force: true });
}

const context = await esbuild.context({
  entryPoints: {
    extension: "src/extension.ts",
    java: "src/java.ts"
  },
  bundle: true,
  format: "cjs",
  platform: "node",
  target: "node20",
  outdir: "dist",
  external: ["vscode"],
  minify: production,
  sourcemap: production ? false : "linked",
  logLevel: "info"
});

if (watch) {
  await context.watch();
} else {
  await context.rebuild();
  await context.dispose();
}
