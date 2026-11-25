// A live window onto the running emulator, for looking at the app in a browser.
//
// Android has no hot reload: a code change is a rebuild and a reinstall. What this does is stream
// whatever the emulator is showing, so the install is the only wait. It is a development tool and
// ships with nothing.
//
//   PORT=8791 node tools/live-view/server.mjs
//
// Screenshots are taken with `adb exec-out screencap -p`, one connection at a time, and the page
// asks for the next one as soon as the last has painted — faster than a timer, and it never
// queues captures behind a slow emulator.

import { execFile } from "node:child_process";
import { createServer } from "node:http";
import process from "node:process";

const PORT = Number(process.env.PORT || 8791);
const ADB = process.env.ADB || "adb";

const page = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Spliit — emulator</title>
<style>
  html, body { margin: 0; height: 100%; background: #0b0b0c; }
  body { display: grid; grid-template-rows: auto 1fr; font: 13px/1.4 -apple-system, "Segoe UI", Roboto, sans-serif; color: #9a9aa2; }
  header { display: flex; gap: 10px; align-items: center; padding: 8px 12px; border-bottom: 1px solid #1e1e22; }
  #dot { width: 8px; height: 8px; border-radius: 50%; background: #3f3f46; }
  #dot.live { background: #34d399; }
  main { display: grid; place-items: center; overflow: hidden; padding: 10px; }
  img { max-width: 100%; max-height: 100%; object-fit: contain; border-radius: 10px; background: #000; display: block; }
  button { margin-left: auto; background: #1a1a1e; color: #d4d4d8; border: 1px solid #2a2a30; border-radius: 8px; padding: 4px 10px; font: inherit; cursor: pointer; }
</style>
</head>
<body>
<header><span id="dot"></span><span id="status">connecting</span><button id="pause">Pause</button></header>
<main><img id="screen" alt="emulator screen"></main>
<script>
  const img = document.getElementById("screen");
  const dot = document.getElementById("dot");
  const status = document.getElementById("status");
  let paused = false;
  let failures = 0;

  document.getElementById("pause").onclick = (event) => {
    paused = !paused;
    event.target.textContent = paused ? "Resume" : "Pause";
    if (!paused) frame();
  };

  function frame() {
    if (paused) return;
    const next = new Image();
    next.onload = () => {
      img.src = next.src;
      dot.className = "live";
      status.textContent = "live";
      failures = 0;
      setTimeout(frame, 80);
    };
    next.onerror = () => {
      // The emulator is booting, or adb went away. Wait longer each time, then keep trying.
      failures += 1;
      dot.className = "";
      status.textContent = failures > 2 ? "waiting for emulator" : "no frame yet";
      setTimeout(frame, Math.min(3000, 300 * failures));
    };
    next.src = "/shot?t=" + Date.now();
  }

  frame();
</script>
</body>
</html>`;

function capture() {
  return new Promise((resolve, reject) => {
    execFile(ADB, ["exec-out", "screencap", "-p"], {
      encoding: "buffer",
      maxBuffer: 32 * 1024 * 1024,
      timeout: 10_000,
    }, (error, stdout) => {
      if (error) reject(error);
      else resolve(stdout);
    });
  });
}

createServer(async (request, response) => {
  const url = new URL(request.url, "http://localhost");
  if (url.pathname === "/shot") {
    try {
      const png = await capture();
      response.writeHead(200, {
        "content-type": "image/png",
        "cache-control": "no-store",
        "content-length": png.length,
      });
      response.end(png);
    } catch (error) {
      response.writeHead(503, { "content-type": "text/plain" });
      response.end(`no frame: ${error.message}`);
    }
    return;
  }

  response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
  response.end(page);
}).listen(PORT, "127.0.0.1", () => {
  console.log(`live view on http://127.0.0.1:${PORT}`);
});
