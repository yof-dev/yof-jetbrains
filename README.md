# yof for JetBrains IDEs

Leaked keys and made-up packages, marked in the editor when you save, each with a plain explanation and a quick fix that copies a prompt for the AI assistant that wrote the code. Works in IntelliJ IDEA, WebStorm, PyCharm, GoLand and the other JetBrains IDEs (2024.1 and newer).

It runs the open-source [yof CLI](https://github.com/yof-dev/yof), bundled with the plugin, on your machine. Rules only, no account, no telemetry.

## What it does

- **On save**, `yof check` runs for the project in a background task. The editor never waits for it: a newer save replaces a pending check, every check has a time limit, and you can cancel it from the progress bar.
- **In the editor**, findings show as errors (fix now) and warnings (fix this week), with the masked value and what can happen.
- **Quick fix** (Alt+Enter): "yof: Copy the fix prompt for your AI assistant". It carries the masked value only.
- **The yof tool window** shows what each check and attack said.
- **After Tools > yof > Check This Project**: Copy fix prompt (every open finding's prompt, numbered), Get a signed report and Turn on Patch. The last two only open app.yof.io in your browser and read "(opens soon)" until ordering opens. A [sample report](https://yof.io/sample) of a made-up site shows what a signed report holds, SBOM included.
- **Tools > yof > Attack My App** runs `yof attack` against your own app (by default `http://localhost:3000`) with your `.yof/attack.json`, after you confirm in a notification. It runs in the background and you can cancel it.

## Private by default

- No network call unless you turn on package lookups in Settings > Tools > yof (then package names and pinned versions go to npm, PyPI and OSV).
- The bundled CLI runs with the Node.js on your PATH (or the one you set). Nothing is downloaded.
- The settings live in the IDE, not in the project, so a project can't make the plugin run a program of its own.
- yof attack goes only where the CLI allows it: localhost, a preview GitHub reports as a deployment of your repository, or your live host with your yof proof token, with your written consent in `.yof/attack.json`.

## Building

Needs JDK 17, Gradle 8.10 and Node.js 20.

```sh
npm install --no-save @yof/cli@0.2.0   # the CLI the plugin bundles (gradle.properties: yofCliVersion)
gradle test buildPlugin                # build/distributions/yof-jetbrains-<version>.zip
gradle runIde                          # a sandbox IDE with the plugin loaded
```

## Licence

Apache 2.0. Security reports: security@yof.io (see SECURITY.md).
