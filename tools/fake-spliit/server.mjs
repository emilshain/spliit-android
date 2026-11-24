// A local stand-in for a Spliit instance, so the app's screens can be driven on an emulator with
// real payloads. It serves the test fixtures verbatim (they are already full tRPC envelopes), so
// the client, its superjson envelope and its decoding are the production ones; only the network
// is replaced.
//
//   node tools/fake-spliit/server.mjs            # http://0.0.0.0:8790
//   PORT=9000 node tools/fake-spliit/server.mjs
//
// See docs/LOCAL_TESTING.md.
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

const FIXTURES = process.env.FIXTURES_DIR ??
  resolve(here, "../../app/src/test/resources/fixtures");

const read = (name) => readFileSync(join(FIXTURES, name), "utf8");

const fixtures = {
  "groups.get": read("groups-get.json"),
  "groups.list": read("groups-list.json"),
  "groups.expenses.list": read("expenses-list.json"),
  "groups.expenses.get": read("expenses-get.json"),
  "groups.balances.list": read("balances-list.json"),
  "groups.stats.get": read("groups-stats-get.json"),
  "groups.stats.overview": read("groups-stats-get.json"),
  "groups.activities.list": read("activities-list.json"),
  "categories.list": read("categories-list.json"),
};

const notFound = (path) =>
  JSON.stringify({
    error: {
      json: {
        message: `No procedure found on path ${path}`,
        code: -32004,
        data: { code: "NOT_FOUND", httpStatus: 404, path },
      },
    },
  });

// The search screen asks the server to filter; the fixture has a handful of expenses, so filter by
// title the way the real instance would.
function filteredExpenses(raw) {
  let filter = null;
  const match = /"filter"\s*:\s*"((?:[^"\\]|\\.)*)"/.exec(decodeURIComponent(raw));
  if (match) filter = JSON.parse(`"${match[1]}"`);
  if (!filter) return fixtures["groups.expenses.list"];
  const envelope = JSON.parse(fixtures["groups.expenses.list"]);
  const needle = filter.toLowerCase();
  envelope.result.data.json.expenses =
    envelope.result.data.json.expenses.filter((expense) =>
      expense.title.toLowerCase().includes(needle)
    );
  return JSON.stringify(envelope);
}

const port = Number(process.env.PORT ?? 8790);

const server = createServer((request, response) => {
  let body = "";
  request.on("data", (chunk) => (body += chunk));
  request.on("end", () => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const path = url.pathname.replace(/^\/api\/trpc\//, "");
    console.log(`${request.method} ${path}`);

    const send = (payload, status = 200) => {
      const text = Buffer.from(payload, "utf8");
      response.writeHead(status, {
        "content-type": "application/json",
        "content-length": text.length,
      });
      response.end(text);
    };

    if (path === "groups.expenses.list") {
      send(filteredExpenses(`${url.search}\n${body}`));
      return;
    }
    const payload = fixtures[path];
    if (payload === undefined) {
      console.log("  -> 404 (unknown procedure)");
      send(notFound(path), 404);
      return;
    }
    send(payload);
  });
});

// OkHttp keeps connections alive between pages; Node's 5s default drops one under a reused
// connection, which surfaces in the app as "unexpected end of stream".
server.keepAliveTimeout = 120_000;
server.headersTimeout = 130_000;

server.listen(port, "0.0.0.0", () => {
  console.log(`fake spliit instance on http://0.0.0.0:${port} from ${FIXTURES}`);
});
