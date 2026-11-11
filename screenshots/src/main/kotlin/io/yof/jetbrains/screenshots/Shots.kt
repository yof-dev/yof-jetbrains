package io.yof.jetbrains.screenshots

import com.intellij.remoterobot.RemoteRobot
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.io.Serializable
import java.time.Duration
import javax.imageio.ImageIO

/*
 * The three JetBrains Marketplace listing screenshots (SCREENSHOTS.md), taken in a real IDE on Xvfb.
 *
 * The IDE is `gradle runIdeForUiTests` from the plugin build: IntelliJ IDEA Community with this plugin and JetBrains'
 * Robot Server plugin. This program asks the IDE to do things with small scripts (Remote-Robot runs them in the IDE
 * with Rhino, with the IntelliJ API at hand), and uses java.awt.Robot on the same X display for the mouse, a key and
 * the screen capture.
 *
 * Arguments: <output folder> <second file to add before scene 3>
 * Environment: YOF_NODE (the Node.js that runs the bundled yof CLI), ROBOT_URL (default http://127.0.0.1:8082).
 *
 * The scripts use the plugin's real words: the annotation "yof: <name> (fix now): <masked>. <why>", the quick fix
 * "yof: Copy the fix prompt for your AI assistant", the tool window id "yof", the action id "io.yof.jetbrains.check"
 * (Tools > yof > Check This Project) and the notification "yof: N to fix. Paste the fix prompt ...".
 */

const val W = 1280
const val H = 800

/** Helpers every script starts with. No "$" in here: these are Kotlin raw strings. */
private val PRELUDE = """
function J(s) { return new java.lang.StringBuilder().append(String(s)).toString(); }
function project() {
  var ps = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
  return ps.length > 0 ? ps[0] : null;
}
function yofClass(name) {
  var d = com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId("io.yof.jetbrains"));
  return d.getPluginClassLoader().loadClass("io.yof.jetbrains." + name);
}
function svc() { return project().getService(yofClass("YofProjectService")); }
function frame() { return com.intellij.openapi.wm.WindowManager.getInstance().getFrame(project()); }
function fem() { return com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project()); }
function femx() { return com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.getInstanceEx(project()); }
function vfile(rel) {
  var fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
  var path = project().getBasePath() + "/" + rel;
  var f = fs.findFileByPath(path);
  return f != null ? f : fs.refreshAndFindFileByPath(path);
}
function textEditorOf(f) {
  if (f == null) return null;
  var eds = fem().getEditors(f);
  for (var i = 0; i < eds.length; i++) { if (eds[i] instanceof com.intellij.openapi.fileEditor.TextEditor) return eds[i].getEditor(); }
  return null;
}
function configEditor() { return textEditorOf(vfile("src/config.ts")); }
function walk(c, fn) {
  if (fn(c)) return c;
  if (c instanceof java.awt.Container) {
    var kids = c.getComponents();
    for (var i = 0; i < kids.length; i++) { var r = walk(kids[i], fn); if (r != null) return r; }
  }
  return null;
}
function findShowing(fn) {
  var ws = java.awt.Window.getWindows();
  for (var i = 0; i < ws.length; i++) {
    if (!ws[i].isShowing()) continue;
    var r = walk(ws[i], function (c) { return c.isShowing() && fn(c); });
    if (r != null) return r;
  }
  return null;
}
function textOf(c) {
  try {
    if (c instanceof javax.swing.text.JTextComponent || c instanceof javax.swing.JLabel || c instanceof javax.swing.AbstractButton) {
      var t = c.getText();
      // HTML panes wrap their source text: compare with single spaces.
      return t == null ? "" : String(t).replace(/\s+/g, " ");
    }
  } catch (e) {}
  return "";
}
function showsText(needle) { return findShowing(function (c) { return textOf(c).indexOf(needle) >= 0; }) != null; }
function screenCenter(c) {
  var p = c.getLocationOnScreen();
  return J(Math.floor(p.x + c.getWidth() / 2) + "," + Math.floor(p.y + c.getHeight() / 2));
}
function expireNotifications() {
  var ns = com.intellij.notification.NotificationsManager.getNotificationsManager()
    .getNotificationsOfType(com.intellij.notification.Notification, project());
  for (var i = 0; i < ns.length; i++) ns[i].expire();
}
function hideBanners() {
  var n = 0;
  walk(frame(), function (c) {
    if (c instanceof com.intellij.ui.EditorNotificationPanel && c.isVisible()) { c.setVisible(false); n++; }
    return false;
  });
  return n;
}
function keyRange(ed) {
  var t = String(ed.getDocument().getText());
  var s = t.indexOf("sk_live_");
  var e = s;
  while (e < t.length && /[A-Za-z0-9_]/.test(t.charAt(e))) e++;
  return [s, e];
}
function toolWindow(id, visible) {
  var tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project()).getToolWindow(id);
  if (tw == null) return false;
  if (visible) tw.show(null); else tw.hide(null);
  return true;
}
// The editor keeps the first 12 characters of the key (sk_live_ and four more, as yof masks it) and folds the rest
// behind bullets, so no full key is ever on screen, not even this made-up one. The caret stays outside the fold.
function maskKeyAndPlaceCaret(ed) {
  var r = keyRange(ed);
  if (r[0] < 0) return "no key in the editor";
  var from = r[0] + 12, to = r[1];
  var fm = ed.getFoldingModel();
  fm.runBatchFoldingOperation(function () {
    var region = fm.getFoldRegion(from, to);
    if (region == null) region = fm.addFoldRegion(from, to, "••••••••");
    if (region != null) region.setExpanded(false);
  });
  ed.getCaretModel().moveToOffset(r[0] + 3);
  ed.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
  ed["setFontSize(int)"](14);
  return "masked " + (to - from) + " characters";
}
"""

class Ide(private val robot: RemoteRobot) {
    /** Runs a script in the IDE (on the UI thread unless edt = false) and returns its value as text. */
    fun js(body: String, edt: Boolean = true): String {
        val r: Serializable = robot.callJs(PRELUDE + "\n" + body, edt)
        return r.toString()
    }

    fun screenshotFromIde(): BufferedImage = robot.getScreenshot()
}

private val log = StringBuilder()

fun say(line: String) {
    val l = "[${java.time.LocalTime.now().withNano(0)}] $line"
    println(l)
    log.append(l).append('\n')
}

fun waitUntil(what: String, timeout: Duration, everyMs: Long = 1000, check: () -> Boolean): Boolean {
    val end = System.nanoTime() + timeout.toNanos()
    var last: Throwable? = null
    while (System.nanoTime() < end) {
        try {
            if (check()) return true
        } catch (e: Throwable) {
            last = e
        }
        Thread.sleep(everyMs)
    }
    say("gave up waiting for: $what" + (last?.let { " (last error: ${it.message?.lineSequence()?.firstOrNull()})" } ?: ""))
    return false
}

/** A JavaScript string literal. */
fun q(s: String): String = buildString {
    append('"')
    for (ch in s) when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        else -> if (ch.code < 0x20 || ch.code > 0x7e) append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}

class Shots(private val ide: Ide, private val out: File, private val second: File) {
    private val awt = Robot().apply { autoDelay = 40 }

    private fun mouse(xy: String) {
        val (x, y) = xy.split(",").map { it.trim().toInt() }
        awt.mouseMove(x - 40, y + 2)
        awt.mouseMove(x - 10, y)
        awt.mouseMove(x, y)
    }

    private fun key(code: Int, vararg modifiers: Int) {
        modifiers.forEach { awt.keyPress(it) }
        awt.keyPress(code)
        awt.keyRelease(code)
        modifiers.reversed().forEach { awt.keyRelease(it) }
    }

    /** The whole Xvfb screen, made exactly 1280 x 800 (cropped from the top left, scaled only if smaller). */
    fun capture(name: String) {
        val raw = try {
            awt.createScreenCapture(Rectangle(0, 0, W, H))
        } catch (e: Exception) {
            say("java.awt.Robot capture failed (${e.message}), asking the IDE for the screen")
            ide.screenshotFromIde()
        }
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        if (raw.width >= W && raw.height >= H) g.drawImage(raw.getSubimage(0, 0, W, H), 0, 0, null)
        else g.drawImage(raw, 0, 0, W, H, null)
        g.dispose()
        val file = File(out, name)
        ImageIO.write(img, "png", file)
        say("saved ${file.name} (${img.width} x ${img.height}, screen was ${raw.width} x ${raw.height})")
    }

    fun setUp(node: String) {
        say("IDE: " + ide.js("""
            var notes = [];
            var lm = com.intellij.ide.ui.LafManager.getInstance();
            var light = lm.getDefaultLightLaf();
            if (light != null && lm.getCurrentUIThemeLookAndFeel() != light) {
              try { com.intellij.ide.actions.QuickChangeLookAndFeel.switchLafAndUpdateUI(lm, light, false); }
              catch (e) { lm.setCurrentLookAndFeel(light, false); lm.updateUI(); notes.push("theme fallback: " + e); }
            }
            var ecm = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance();
            if (ecm.isDarkEditor()) {
              var names = ["Light", "IntelliJ Light", "Default"];
              for (var i = 0; i < names.length; i++) { var s = ecm.getScheme(names[i]); if (s != null) { ecm.setGlobalScheme(s); break; } }
            }
            notes.push("theme " + lm.getCurrentUIThemeLookAndFeel().getName() + ", dark editor " + ecm.isDarkEditor()
              + ", new UI " + com.intellij.ui.ExperimentalUI.isNewUI());
            var f = frame();
            f.setExtendedState(java.awt.Frame.NORMAL);
            f.setBounds(0, 0, ${W}, ${H});
            f.validate();
            var settings = com.intellij.openapi.application.ApplicationManager.getApplication().getService(yofClass("YofSettings")).getState();
            settings.setNodePath(${q(node)});
            femx().closeAllFiles();
            expireNotifications();
            J(notes.join("; "))
        """))
    }

    /** 1. The leaked key marked in the editor, its tooltip, and the yof tool window with the check line. */
    fun scene1() {
        say(ide.js("""
            fem().openFile(vfile("src/config.ts"), true);
            toolWindow("Project", true);
            svc().checkNow(false);
            J("check started")
        """))
        val checked = waitUntil("the first yof check", Duration.ofMinutes(3)) {
            ide.js("""J(svc().findingsFor("src/config.ts").size() > 0 && String(svc().logText()).indexOf("[check]") >= 0)""") == "true"
        }
        say("yof tool window log: " + ide.js("J(svc().logText())").trim().replace("\n", " | "))
        if (!checked) error("yof check found nothing in src/config.ts")
        waitUntil("the yof highlight in the editor", Duration.ofSeconds(60)) {
            ide.js("""
                var ed = configEditor();
                var hs = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(ed.getDocument(), project(), true).getAllHighlighters();
                var found = false;
                for (var i = 0; i < hs.length; i++) {
                  var t = hs[i].getErrorStripeTooltip();
                  if (t != null && t.getDescription && String(t.getDescription()).indexOf("yof:") == 0) found = true;
                }
                J(found)
            """) == "true"
        }
        say(ide.js("""
            var ed = configEditor();
            var m = maskKeyAndPlaceCaret(ed);
            toolWindow("yof", true);
            expireNotifications();
            J(m + ", banners hidden " + hideBanners())
        """))
        Thread.sleep(1500)
        val xy = ide.js("""
            var ed = configEditor();
            var r = keyRange(ed);
            var p = ed.offsetToXY(r[0] + 6);
            var c = ed.getContentComponent().getLocationOnScreen();
            J(Math.floor(c.x + p.x) + "," + Math.floor(c.y + p.y + ed.getLineHeight() / 2))
        """)
        mouse(xy)
        waitUntil("the yof tooltip", Duration.ofSeconds(15), 500) {
            ide.js("""J(showsText("Stripe live secret key"))""") == "true" || run { mouse(xy); false }
        }
        Thread.sleep(800)
        capture("01-leaked-key-in-editor.png")
    }

    /** 2. Alt+Enter on the key with the yof quick fix highlighted, the pasted fix prompt in a split editor. */
    fun scene2() {
        awt.mouseMove(W / 2, 40)
        key(KeyEvent.VK_ESCAPE)
        say(ide.js("""
            toolWindow("Project", false);
            toolWindow("yof", false);
            var prompt = svc().findingsFor("src/config.ts").get(0).getFixPrompt();
            var scratch = com.intellij.ide.scratch.ScratchRootType.getInstance().createScratchFile(project(), "fix prompt.txt",
              com.intellij.openapi.fileTypes.PlainTextLanguage.INSTANCE, prompt);
            fem().openFile(vfile("src/config.ts"), true);
            var split = femx().getCurrentWindow().split(javax.swing.SwingConstants.VERTICAL, true, scratch, false);
            if (split == null) fem().openFile(scratch, false);
            var se = textEditorOf(scratch);
            if (se != null) { se.getSettings().setUseSoftWraps(true); se["setFontSize(int)"](14); }
            var ed = configEditor();
            var m = maskKeyAndPlaceCaret(ed);
            com.intellij.openapi.wm.IdeFocusManager.getInstance(project()).requestFocus(ed.getContentComponent(), true);
            J("split " + (split != null) + ", " + m + ", banners hidden " + hideBanners())
        """))
        Thread.sleep(1500)
        say(ide.js("""
            var ed = configEditor();
            var am = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            am.tryToExecute(am.getAction("ShowIntentionActions"), null, ed.getContentComponent(), "EditorPopup", true);
            J("Show Context Actions asked")
        """))
        val item = "Copy the fix prompt for your AI assistant"
        val itemShown = { ide.js("""J(findShowing(function (c) { return c instanceof javax.swing.JList && listIndex(c) >= 0; }) != null)""".withListIndex(item)) == "true" }
        if (!waitUntil("the Alt+Enter menu", Duration.ofSeconds(10), 500, itemShown)) {
            say("pressing Alt+Enter instead")
            key(KeyEvent.VK_ENTER, KeyEvent.VK_ALT)
            if (!waitUntil("the Alt+Enter menu after the key", Duration.ofSeconds(10), 500, itemShown)) error("the yof quick fix is not in the Alt+Enter menu")
        }
        say(ide.js("""
            var list = findShowing(function (c) { return c instanceof javax.swing.JList && listIndex(c) >= 0; });
            var i = listIndex(list);
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
            J("selected item " + i + " of " + list.getModel().getSize())
        """.withListIndex(item)))
        Thread.sleep(1200)
        capture("02-fix-prompt-quick-fix.png")
        key(KeyEvent.VK_ESCAPE)
    }

    /** 3. Tools > yof open after Check This Project, with the next-steps notification in the lower right. */
    fun scene3() {
        say(ide.js("""
            var files = fem().getOpenFiles();
            for (var i = 0; i < files.length; i++) { if (String(files[i].getName()).indexOf("fix prompt") == 0) fem().closeFile(files[i]); }
            femx().unsplitAllWindow();
            J(project().getBasePath())
        """).let { base ->
            second.copyTo(File(base, "src/${second.name}"), overwrite = true)
            "added src/${second.name}"
        })
        say(ide.js("""
            vfile("src/${second.name}");
            fem().openFile(vfile("src/config.ts"), true);
            toolWindow("Project", true);
            toolWindow("yof", true);
            expireNotifications();
            var ed = configEditor();
            var m = maskKeyAndPlaceCaret(ed);
            var am = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            am.tryToExecute(am.getAction("io.yof.jetbrains.check"), null, ed.getContentComponent(), "MainMenu", true);
            J(m + ", Check This Project asked, banners hidden " + hideBanners())
        """))
        val needle = "to fix. Paste the fix prompt"
        val notifications = {
            ide.js("""
                var ns = com.intellij.notification.NotificationsManager.getNotificationsManager()
                  .getNotificationsOfType(com.intellij.notification.Notification, project());
                var t = [];
                for (var i = 0; i < ns.length; i++) t.push(String(ns[i].getContent()));
                J(t.join(" | "))
            """)
        }
        if (!waitUntil("the next-steps notification", Duration.ofMinutes(3)) { notifications().contains(needle) }) {
            say("notifications: " + notifications())
            error("the next-steps notification did not come")
        }
        say("notification: " + notifications())
        waitUntil("the notification balloon on screen", Duration.ofSeconds(10), 500) { ide.js("""J(showsText(${q(needle)}))""") == "true" }
        say("yof tool window log: " + ide.js("J(svc().logText())").trim().replace("\n", " | "))
        Thread.sleep(1500)
        openToolsYofMenu()
        Thread.sleep(1000)
        capture("03-check-and-next-steps.png")
        key(KeyEvent.VK_ESCAPE)
        key(KeyEvent.VK_ESCAPE)
    }

    private val menuHelpers = """
        function menuNamed(text) {
          return findShowing(function (c) { return c instanceof javax.swing.JMenuItem && String(c.getText()) == text; });
        }
        function select(items) {
          var arr = java.lang.reflect.Array.newInstance(javax.swing.MenuElement, items.length);
          for (var i = 0; i < items.length; i++) arr[i] = items[i];
          javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(arr);
        }
        function toolsPath() {
          var tools = menuNamed("Tools");
          if (tools == null) return null;
          var parent = tools.getParent();
          return [parent, tools, tools.getPopupMenu()];
        }
    """

    /** Opens Tools > yof without clicking (a click could close the notification): the menu bar, or the New UI main menu button. */
    private fun openToolsYofMenu() {
        val hasTools = { ide.js(menuHelpers + """J(menuNamed("Tools") != null)""") == "true" }
        if (!hasTools()) {
            val button = ide.js("""
                var b = findShowing(function (c) {
                  var n = String(c.getClass().getName());
                  return n.indexOf("MainMenuButton") >= 0 || (c instanceof javax.swing.JComponent && String(c.getToolTipText()) == "Main Menu");
                });
                J(b == null ? "" : screenCenter(b))
            """)
            say("no visible menu bar; main menu button at '$button'")
            if (button.isNotBlank()) {
                awt.mouseMove(button.substringBefore(",").toInt(), button.substringAfter(",").toInt())
                awt.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                awt.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            }
            if (!waitUntil("the Tools menu", Duration.ofSeconds(10), 500, hasTools)) error("Tools menu not found")
        }
        say(ide.js(menuHelpers + """ select(toolsPath()); J("Tools opened") """))
        val yofShown = { ide.js(menuHelpers + """J(menuNamed("yof") instanceof javax.swing.JMenu)""") == "true" }
        if (!waitUntil("yof in the Tools menu", Duration.ofSeconds(10), 500, yofShown)) error("yof is not in the Tools menu")
        say(ide.js(menuHelpers + """
            var yof = menuNamed("yof");
            select(toolsPath().concat([yof, yof.getPopupMenu()]));
            J("Tools > yof opened")
        """))
        if (waitUntil("Check This Project in Tools > yof", Duration.ofSeconds(10), 500) {
                ide.js(menuHelpers + """J(menuNamed("Check This Project") != null)""") == "true"
            }) {
            say(ide.js(menuHelpers + """
                var yof = menuNamed("yof");
                select(toolsPath().concat([yof, yof.getPopupMenu(), menuNamed("Check This Project")]));
                var seen = [];
                var items = yof.getPopupMenu().getComponents();
                for (var i = 0; i < items.length; i++) if (items[i] instanceof javax.swing.JMenuItem) seen.push(String(items[i].getText()));
                J("Tools > yof shows: " + seen.join(", "))
            """))
        }
    }
}

/** Adds listIndex(list): the index of the row whose text contains [item], or -1. */
private fun String.withListIndex(item: String) = """
    function listIndex(list) {
      var m = list.getModel();
      for (var i = 0; i < m.getSize(); i++) {
        var v = m.getElementAt(i);
        var t = "";
        try { t = (v != null && v.getText) ? String(v.getText()) : String(v); } catch (e) { t = String(v); }
        if (t.indexOf(${q(item)}) >= 0) return i;
      }
      return -1;
    }
""" + this

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <output folder> <second file to add before scene 3>" }
    val out = File(args[0]).apply { mkdirs() }
    val second = File(args[1])
    val ide = Ide(RemoteRobot(System.getenv("ROBOT_URL") ?: "http://127.0.0.1:8082"))
    val shots = Shots(ide, out, second)
    var failed = 0
    try {
        val ready = waitUntil("the project to open and indexing to finish", Duration.ofMinutes(20), 5000) {
            ide.js("""
                var p = project();
                J(p != null && frame() != null && frame().isShowing() && !com.intellij.openapi.project.DumbService.isDumb(p))
            """) == "true"
        }
        if (!ready) error("the IDE did not open the demo project")
        Thread.sleep(5000)
        shots.setUp(System.getenv("YOF_NODE") ?: "node")
        Thread.sleep(3000)
        for ((name, scene) in listOf("scene 1" to shots::scene1, "scene 2" to shots::scene2, "scene 3" to shots::scene3)) {
            say("== $name")
            try {
                scene()
            } catch (e: Throwable) {
                failed++
                say("$name FAILED: ${e.message}")
                runCatching { shots.capture("debug-${name.replace(' ', '-')}.png") }
            }
        }
    } catch (e: Throwable) {
        failed++
        say("FAILED: ${e.message}")
        runCatching { shots.capture("debug-start.png") }
    } finally {
        File(out, "driver-log.txt").writeText(log.toString())
    }
    if (failed > 0) {
        System.err.println("$failed step(s) failed; see driver-log.txt and the debug-*.png files")
        kotlin.system.exitProcess(1)
    }
}
