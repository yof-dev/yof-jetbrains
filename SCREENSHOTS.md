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
