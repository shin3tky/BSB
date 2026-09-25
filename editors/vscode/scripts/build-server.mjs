import { copyFile, mkdir } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const extensionDirectory = path.resolve(scriptDirectory, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..", "..");
const gradleWrapper = path.join(
  repositoryRoot,
  process.platform === "win32" ? "gradlew.bat" : "gradlew"
);

const build = spawnSync(
  gradleWrapper,
  [":language-server:shadowJar", "--console=plain"],
  { cwd: repositoryRoot, stdio: "inherit" }
);
if (build.error !== undefined) {
  throw build.error;
}
if (build.status !== 0) {
  throw new Error(`Language Serverのビルドに失敗しました: exit ${build.status}`);
}

const source = path.join(
  repositoryRoot,
  "language-server",
  "build",
  "libs",
  "language-server-all.jar"
);
const serverDirectory = path.join(extensionDirectory, "server");
await mkdir(serverDirectory, { recursive: true });
await copyFile(source, path.join(serverDirectory, "language-server-all.jar"));
await copyFile(
  path.join(repositoryRoot, "LICENSE"),
  path.join(extensionDirectory, "LICENSE")
);
