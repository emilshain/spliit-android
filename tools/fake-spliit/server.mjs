// A local stand-in for a Spliit instance, so the app's screens can be driven on an emulator with
// real payloads. It serves the test fixtures verbatim (they are already full tRPC envelopes), so
// the client, its superjson envelope and its decoding are the production ones; only the network
// is replaced.
//
//   PORT=8790 node tools/fake-spliit/server.mjs
//
// Knobs, all optional:
//   PORT              default 8790 (the environment sometimes carries PORT=0, which is why the
//                     default is only used when it is falsy)
//   PAGE_SIZE         expenses per page, default 10 — the client asks for 20 and pages on scroll
//   EXTRA_EXPENSES    padding expenses added to the four fixtures, default 26, so the list is
//                     longer than a screen and really pages
//   FIXTURES_DIR      where the fixtures are read from
//
// Two behaviours differ from a fixture dump, deliberately:
//   - a delete removes the expense, so reloading shows it gone;
//   - the stats totals are summed from the served expenses, so the totals tab keeps reconciling
//     with the category sweep however many expenses there are.
//
// See docs/LOCAL_TESTING.md.
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

const FIXTURES = process.env.FIXTURES_DIR ||
  resolve(here, "../../app/src/test/resources/fixtures");

const PAGE_SIZE = Number(process.env.PAGE_SIZE || 10);
const EXTRA_EXPENSES = Number(process.env.EXTRA_EXPENSES ?? 26);

const read = (name) => readFileSync(join(FIXTURES, name), "utf8");

const fixtures = {
  "groups.get": read("groups-get.json"),
  "groups.list": read("groups-list.json"),
  "groups.expenses.get": read("expenses-get.json"),
  "groups.balances.list": read("balances-list.json"),
  "groups.activities.list": read("activities-list.json"),
  "categories.list": read("categories-list.json"),
};

const fixtureExpenses = JSON.parse(read("expenses-list.json")).result.data.json.expenses;

/** The expense list, mutable: a delete takes one out and every later page reflects it. */
const expenses = [...fixtureExpenses];

// Padding so the first page does not fill the screen: a list that already fits never scrolls far
// enough to ask for page two. Older than the fixtures, so they sort after them.
const template = fixtureExpenses[fixtureExpenses.length - 1];
for (let i = 1; i <= EXTRA_EXPENSES; i++) {
  const day = new Date(Date.UTC(2025, 5, 1) - i * 3 * 24 * 60 * 60 * 1000);
  const participant = template.paidFor[i % template.paidFor.length].participant;
  expenses.push({
    ...template,
    id: `fake${String(i).padStart(3, "0")}Expense`,
    title: `Padding expense ${i}`,
    amount: 100 + ((i * 137) % 2500),
    expenseDate: day.toISOString(),
    createdAt: day.toISOString(),
    paidBy: participant,
    paidFor: template.paidFor.map((share) => ({
      ...share,
      participant: share.participant,
    })),
  });
}

const groupParticipants = JSON.parse(fixtures["groups.get"]).result.data.json.group.participants;
const categoryList = JSON.parse(fixtures["categories.list"]).result.data.json.categories;

/** The request body, as the client sent it: a plain JSON tRPC envelope. */
function parseBody(body) {
  try {
    const parsed = JSON.parse(body || "{}");
    // The client sends its input inside a superjson envelope.
    return parsed && typeof parsed.json === "object" && parsed.json !== null ? parsed.json : parsed;
  } catch {
    return {};
  }
}

/** An expense row in the shape the list and the stats are served in. */
function expenseFromValues(id, values, previous = null) {
  const participant = (participantId) =>
    groupParticipants.find((entry) => entry.id === participantId) ||
    previous?.paidFor?.find((entry) => entry.participant.id === participantId)?.participant || {
      id: participantId,
      name: "Someone",
    };
  const categoryId = values.category ?? previous?.category?.id ?? 0;
  const category = categoryList.find((entry) => entry.id === categoryId) || null;
  return {
    id,
    title: values.title ?? previous?.title ?? "Untitled",
    amount: values.amount ?? previous?.amount ?? 0,
    createdAt: previous?.createdAt ?? new Date().toISOString(),
    expenseDate: values.expenseDate ?? previous?.expenseDate ?? new Date().toISOString(),
    isReimbursement: values.isReimbursement ?? previous?.isReimbursement ?? false,
    splitMode: values.splitMode ?? previous?.splitMode ?? "EVENLY",
    recurrenceRule: values.recurrenceRule === "NONE" ? null : previous?.recurrenceRule ?? null,
    category,
    paidBy: participant(values.paidBy ?? previous?.paidBy?.id),
    paidFor: (values.paidFor ?? previous?.paidFor ?? []).map((share) => ({
      participant: participant(share.participant.id ?? share.participant),
      shares: share.shares,
    })),
    _count: { documents: 0 },
  };
}

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

const voidResult = JSON.stringify({ result: { data: { json: null } } });

/** `input` is the superjson envelope; these are the only fields this stand-in reads from it. */
function numberField(raw, name) {
  const match = new RegExp(`"${name}"\\s*:\\s*(-?\\d+)`).exec(decodeURIComponent(raw));
  return match ? Number(match[1]) : null;
}

function stringField(raw, name) {
  const match = new RegExp(`"${name}"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)"`).exec(decodeURIComponent(raw));
  return match ? JSON.parse(`"${match[1]}"`) : null;
}

/** One page of the expense list, with the search `filter` applied the way the instance would. */
function expensesPage(raw) {
  const filter = stringField(raw, "filter");
  const cursor = numberField(raw, "cursor") ?? 0;
  const limit = Math.min(numberField(raw, "limit") ?? PAGE_SIZE, PAGE_SIZE);

  const matching = filter
    ? expenses.filter((expense) => expense.title.toLowerCase().includes(filter.toLowerCase()))
    : expenses;

  const start = Math.max(0, Math.min(cursor, matching.length));
  const end = Math.min(start + limit, matching.length);

  return JSON.stringify({
    result: {
      data: {
        json: {
          expenses: matching.slice(start, end),
          hasMore: end < matching.length,
          nextCursor: end,
        },
      },
    },
  });
}

const sharesTotal = (expense) =>
  expense.paidFor.reduce((sum, share) => sum + share.shares, 0) || 1;

/** The totals the stats tab prints, summed from what this instance is actually serving. */
function stats(raw) {
  const participantId = stringField(raw, "participantId");
  const spent = expenses.filter((expense) => !expense.isReimbursement);

  const group = spent.reduce((sum, expense) => sum + expense.amount, 0);
  if (!participantId) {
    return JSON.stringify({ result: { data: { json: { totalGroupSpendings: group } } } });
  }

  const paid = spent
    .filter((expense) => expense.paidBy.id === participantId)
    .reduce((sum, expense) => sum + expense.amount, 0);

  const share = spent.reduce((sum, expense) => {
    const mine = expense.paidFor.find((entry) => entry.participant.id === participantId);
    if (!mine) return sum;
    return sum + (expense.amount * mine.shares) / sharesTotal(expense);
  }, 0);

  return JSON.stringify({
    result: {
      data: {
        json: {
          totalGroupSpendings: group,
          totalParticipantSpendings: paid,
          totalParticipantShare: share,
        },
      },
    },
  });
}

const port = Number(process.env.PORT || 8790);

const server = createServer((request, response) => {
  let body = "";
  request.on("data", (chunk) => (body += chunk));
  request.on("end", () => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const path = url.pathname.replace(/^\/api\/trpc\//, "");
    const input = `${url.search}\n${body}`;
    console.log(`${request.method} ${path}`);

    const send = (payload, status = 200) => {
      const text = Buffer.from(payload, "utf8");
      response.writeHead(status, {
        "content-type": "application/json",
        "content-length": text.length,
      });
      response.end(text);
    };

    switch (path) {
      case "groups.expenses.list":
        send(expensesPage(input));
        return;
      case "groups.stats.overview":
      case "groups.stats.get":
        send(stats(input));
        return;
      case "groups.expenses.create": {
        const input = parseBody(body);
        const values = input.expenseFormValues || {};
        const id = input.expenseId || `fake${Date.now()}`;
        const expense = expenseFromValues(id, values);
        expenses.unshift(expense);
        console.log(`  -> created "${expense.title}" (${expenses.length} total)`);
        send(JSON.stringify({ result: { data: { json: { expenseId: id } } } }));
        return;
      }
      case "groups.expenses.update": {
        const input = parseBody(body);
        const id = input.expenseId || stringField(input, "expenseId");
        const at = expenses.findIndex((expense) => expense.id === id);
        if (at === -1) {
          console.log(`  -> 404 (no expense ${id})`);
          send(notFound(path), 404);
          return;
        }
        const values = input.expenseFormValues || {};
        expenses[at] = expenseFromValues(id, values, expenses[at]);
        console.log(`  -> updated "${expenses[at].title}"`);
        send(JSON.stringify({ result: { data: { json: { expenseId: id } } } }));
        return;
      }
      case "groups.expenses.delete": {
        const id = stringField(input, "expenseId");
        const at = expenses.findIndex((expense) => expense.id === id);
        if (at === -1) {
          console.log(`  -> 404 (no expense ${id})`);
          send(notFound(path), 404);
          return;
        }
        const [removed] = expenses.splice(at, 1);
        console.log(`  -> deleted "${removed.title}" (${expenses.length} left)`);
        send(voidResult);
        return;
      }
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
  console.log(
    `fake spliit instance on http://0.0.0.0:${port} — ${expenses.length} expenses, ` +
      `${PAGE_SIZE} per page, from ${FIXTURES}`,
  );
});
