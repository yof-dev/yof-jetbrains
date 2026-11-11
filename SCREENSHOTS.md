# Screenshots for the JetBrains Marketplace listing

Three screenshots, each 1280 x 800 (16:10), PNG. Take them in IntelliJ IDEA with the default light theme (New UI),
editor font size 14, a small demo project such as [yof-dev/yof-demo](https://github.com/yof-dev/yof-demo). Show only
what the plugin does today (0.1.1). Use fake keys only, made for the screenshot and never real ones; the plugin prints
masked values, so check that no full key is visible anywhere on screen, including the editor itself.

1. **A leaked key marked in the editor.** A file such as `src/config.ts` with a fake Stripe live key on one line. The
   line has yof's red underline, and the mouse rests on it so the tooltip shows the plain explanation ("Stripe live
   secret key (fix now): sk_live_…. Anyone can use it to take payments…"). The yof tool window is open at the bottom
   with the latest "[check] 1 to fix now, 0 to fix this week." line.

2. **The fix prompt quick fix.** The same line with the Alt+Enter menu open, the item "yof: Copy the fix prompt for
   your AI assistant" highlighted. In a split editor beside it, a plain scratch file with the pasted prompt, so the
   masked value and "Where: src/config.ts, line 3." are readable.

3. **After a check: the next steps and attack.** Tools > yof > Check This Project has just finished: the notification
   in the lower right reads "yof: 2 to fix. Paste the fix prompt into your AI assistant, or get a signed report for
   your app." with its three links (Copy fix prompt, Get a signed report (opens soon), Turn on Patch (opens soon)).
   The Tools > yof menu is open as well, showing Check This Project, Attack My App (yof attack), Get a Signed Report
   (Opens Soon) and Turn On Patch (Opens Soon).

Do not show: accepted risks or the scan count (not in the JetBrains plugin yet), a paid order, or any other company's
logo or product name in the window title or the tabs.

## Taking them on GitHub Actions

No local IDE needed. In github.com/yof-dev/yof-jetbrains open Actions > screenshots > Run workflow (or
`gh workflow run screenshots.yml -R yof-dev/yof-jetbrains`). It runs only when started by hand and uses no secrets.
The run starts IntelliJ IDEA Community 2024.1.7 with this plugin on a virtual 1280 x 800 screen (Xvfb), opens a small
demo project it makes on the spot (`src/config.ts` with a Stripe-style key made up for the picture, never a real one),
and drives the IDE through JetBrains' Remote-Robot (`gradle runIdeForUiTests` plus the driver in `screenshots/`). It
switches to the Light theme, sets the editor font to 14 and folds every key in the editor down to its first 12
characters, the way yof masks it. Allow 15 to 25 minutes.

Download the artifact `listing-screenshots`: `01-leaked-key-in-editor.png`, `02-fix-prompt-quick-fix.png` and
`03-check-and-next-steps.png`, each 1280 x 800, plus `driver-log.txt`. A scene that fails leaves a `debug-*.png` of
the screen instead, and the run is marked failed; the IDE's own logs are in the `ide-logs` artifact. Look at every
picture before uploading it: the rules above still apply.
