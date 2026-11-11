# Changelog

## 0.1.1 (11 Nov 2026)

- The plugin has its own icon: the yof dog, for light and dark IDE themes (`pluginIcon.svg`, `pluginIcon_dark.svg`).
- After Tools > yof > Check This Project: Copy fix prompt (every open finding's prompt, numbered), Get a signed report and Turn on Patch. The last two only open app.yof.io in your browser, also from the Tools > yof menu, and read "(opens soon)" until ordering opens. No network call from the plugin and no telemetry.
- The Marketplace description mentions AI builders, security, secrets, the SBOM in signed reports and fix prompts.

## 0.1.0 (10 Nov 2026)

- Runs `yof check` on the project when you save, in a background task, and marks the findings in the editor with the CLI's plain words.
- Quick fix that copies the fix prompt for your AI assistant (masked value only).
- The yof tool window, and Settings > Tools > yof.
- Tools > yof > Attack My App: `yof attack` in the background, after you confirm.
- Bundles `@yof/cli` 0.2.0. No network call unless package lookups are on. No telemetry.
