import { execFile } from "node:child_process";

export const MINIMUM_JAVA_VERSION = 25;

export function parseJavaMajorVersion(output: string): number | undefined {
  const versionMatch = /\b(?:java|openjdk) version "(?:1\.)?(\d+)/iu.exec(output);
  if (versionMatch !== null) {
    return Number.parseInt(versionMatch[1], 10);
  }
  const shortMatch = /\b(?:java|openjdk)\s+(\d+)(?:[.\s])/iu.exec(output);
  return shortMatch === null ? undefined : Number.parseInt(shortMatch[1], 10);
}

export async function verifyJava(executable: string): Promise<number> {
  const output = await javaVersionOutput(executable);
  const major = parseJavaMajorVersion(output);
  if (major === undefined) {
    throw new Error(`Javaのバージョンを判定できませんでした: ${executable}`);
  }
  if (major < MINIMUM_JAVA_VERSION) {
    throw new Error(
      `Java ${MINIMUM_JAVA_VERSION}以降が必要です。現在のバージョン: ${major}`
    );
  }
  return major;
}

function javaVersionOutput(executable: string): Promise<string> {
  return new Promise((resolve, reject) => {
    execFile(
      executable,
      ["-version"],
      { timeout: 5_000, windowsHide: true },
      (error, stdout, stderr) => {
        if (error !== null) {
          reject(new Error(`Javaを実行できませんでした: ${executable}`, { cause: error }));
          return;
        }
        resolve(`${stdout}\n${stderr}`);
      }
    );
  });
}
