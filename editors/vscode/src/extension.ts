import * as path from "node:path";
import { access } from "node:fs/promises";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions
} from "vscode-languageclient/node";
import { verifyJava } from "./java";

let client: LanguageClient | undefined;
let outputChannel: vscode.LogOutputChannel | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  outputChannel = vscode.window.createOutputChannel("BSB Language Server", {
    log: true
  });
  context.subscriptions.push(outputChannel);

  try {
    const javaExecutable = configuredJavaPath();
    const javaVersion = await verifyJava(javaExecutable);
    const serverJar = context.asAbsolutePath(
      path.join("server", "language-server-all.jar")
    );
    await access(serverJar);

    outputChannel.appendLine(`Java ${javaVersion}: ${javaExecutable}`);
    outputChannel.appendLine(`Language Server: ${serverJar}`);

    const serverOptions: ServerOptions = {
      command: javaExecutable,
      args: ["-jar", serverJar],
      options: {
        cwd: context.extensionPath
      }
    };
    const clientOptions: LanguageClientOptions = {
      documentSelector: [
        { scheme: "file", language: "bsb" },
        { scheme: "untitled", language: "bsb" }
      ],
      outputChannel
    };

    client = new LanguageClient(
      "bsb",
      "BSB Language Server",
      serverOptions,
      clientOptions
    );
    await client.start();
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    outputChannel.appendLine(message);
    void vscode.window.showErrorMessage(
      `BSB Language Serverを起動できませんでした: ${message}`
    );
  }
}

export async function deactivate(): Promise<void> {
  const running = client;
  client = undefined;
  if (running !== undefined) {
    await running.stop();
  }
}

function configuredJavaPath(): string {
  const configured = vscode.workspace
    .getConfiguration("bsb")
    .get<string>("java.path", "java")
    .trim();
  return configured.length === 0 ? "java" : configured;
}
