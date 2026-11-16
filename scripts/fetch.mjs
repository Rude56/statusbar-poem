import fs from "node:fs";
import path from "node:path";

// 极简断点续传下载器：Node 的 fetch 在本机可用（PowerShell/curl 的 TLS 有问题）。
const [url, dest] = process.argv.slice(2);
if (!url || !dest) {
  console.error("usage: node fetch.mjs <url> <dest>");
  process.exit(2);
}
fs.mkdirSync(path.dirname(dest), { recursive: true });

async function totalSize(u) {
  const r = await fetch(u, { method: "GET", redirect: "follow", headers: { Range: "bytes=0-0" } });
  const cr = r.headers.get("content-range");
  const total = cr ? Number(cr.split("/")[1]) : Number(r.headers.get("content-length") || 0);
  await r.body.cancel();
  return total;
}

let total = 0;
try {
  total = await totalSize(url);
} catch (e) {
  console.error("size probe failed:", e.message);
}

let have = fs.existsSync(dest) ? fs.statSync(dest).size : 0;
if (total && have >= total) {
  console.log("ALREADY_COMPLETE", path.basename(dest));
  process.exit(0);
}

for (let attempt = 1; attempt <= 20; attempt++) {
  have = fs.existsSync(dest) ? fs.statSync(dest).size : 0;
  if (total && have >= total) break;
  try {
    const r = await fetch(url, {
      redirect: "follow",
      headers: have > 0 ? { Range: `bytes=${have}-` } : {},
      signal: AbortSignal.timeout(180_000),
    });
    if (!r.ok && r.status !== 206) throw new Error("HTTP " + r.status);
    const fh = await fs.promises.open(dest, have > 0 ? "a" : "w");
    const reader = r.body.getReader();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      await fh.write(value);
      have += value.length;
    }
    await fh.close();
    if (total && have < total) throw new Error("short read");
    break;
  } catch (e) {
    console.error(`retry ${attempt}: ${e.message}`);
    await new Promise((r) => setTimeout(r, 1500));
  }
}
have = fs.existsSync(dest) ? fs.statSync(dest).size : 0;
console.log("DONE", path.basename(dest), have, "bytes");
process.exit(total && have >= total ? 0 : 1);
