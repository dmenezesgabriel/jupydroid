import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  ChannelClosedError,
  JsonRpcConnection,
  RpcError,
  type RpcHandlers,
} from "./json-rpc.ts";
import { FakeLineChannel } from "./testing/fake-line-channel.ts";
import { assertPartial, waitUntil } from "./testing/assertions.ts";
import { CallRecorder } from "./testing/call-recorder.ts";

function connect(overrides: Partial<RpcHandlers> = {}) {
  const channel = new FakeLineChannel();
  const onNotification = CallRecorder.silent<[string, unknown]>();
  const onFrame = CallRecorder.silent<[]>();
  const handlers: RpcHandlers = {
    onRequest: async () => ({}),
    onNotification: onNotification.fn,
    onFrame: onFrame.fn,
    ...overrides,
  };
  return {
    channel,
    onNotification,
    onFrame,
    rpc: new JsonRpcConnection(channel, handlers),
  };
}

describe("JsonRpcConnection", () => {
  it("resolves a request with the result of the reply carrying its id", async () => {
    const { channel, rpc } = connect();

    const reply = rpc.request("session/new", { cwd: "/repo" });
    assertPartial(channel.lastSent(), {
      jsonrpc: "2.0",
      id: 1,
      method: "session/new",
      params: { cwd: "/repo" },
    });
    channel.deliver({ id: 1, result: { sessionId: "wool-care" } });

    assert.deepEqual(await reply, { sessionId: "wool-care" });
  });

  it("rejects a request with an RpcError carrying code, message and data", async () => {
    const { channel, rpc } = connect();

    const reply = rpc.request("session/prompt", {});
    channel.deliver({
      id: 1,
      error: {
        code: -32011,
        message: "Quota exhausted.",
        data: { "cognition.ai/errorKind": "resource_exhausted" },
      },
    });

    const error = await reply.then(
      () => assert.fail("expected the request to reject"),
      (rejection: unknown) => rejection,
    );
    assert.ok(error instanceof RpcError);
    assertPartial(error, {
      name: "RpcError",
      code: -32011,
      message: "Quota exhausted.",
      data: { "cognition.ai/errorKind": "resource_exhausted" },
    });
  });

  it("matches replies to requests out of order", async () => {
    const { channel, rpc } = connect();

    const first = rpc.request("a", {});
    const second = rpc.request("b", {});
    channel.deliver({ id: 2, result: "second" });
    channel.deliver({ id: 1, result: "first" });

    assert.equal(await first, "first");
    assert.equal(await second, "second");
  });

  it("hands an agent request to the handler and replies with its result", async () => {
    const onRequest = CallRecorder.resolving<[string, unknown], unknown>({
      action: "accept",
    });
    const { channel } = connect({ onRequest: onRequest.fn });

    channel.deliver({
      id: "47bd2f1e",
      method: "elicitation/create",
      params: { message: "Which file name?" },
    });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.deepEqual(onRequest.calls, [
      ["elicitation/create", { message: "Which file name?" }],
    ]);
    assert.deepEqual(channel.lastSent(), {
      jsonrpc: "2.0",
      id: "47bd2f1e",
      result: { action: "accept" },
    });
  });

  it("answers an agent request with a JSON-RPC error when the handler throws", async () => {
    const onRequest = CallRecorder.rejecting<[string, unknown]>(
      new Error("no such method"),
    );
    const { channel } = connect({ onRequest: onRequest.fn });

    channel.deliver({ id: 9, method: "fs/read_text_file", params: {} });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), {
      id: 9,
      error: { message: "no such method" },
    });
  });

  it("passes notifications to the handler and counts every frame as activity", () => {
    const { channel, onNotification, onFrame } = connect();

    channel.deliver({ method: "session/update", params: { sessionId: "s" } });

    assert.deepEqual(onNotification.calls, [
      ["session/update", { sessionId: "s" }],
    ]);
    assert.equal(onFrame.calls.length, 1);
  });

  it("ignores a line that is not JSON and does not count it as activity", () => {
    const { channel, onNotification, onFrame } = connect();

    channel.deliverRaw("2026-11-13T22:02:34Z INFO stray log line");

    assert.deepEqual(onNotification.calls, []);
    assert.deepEqual(onFrame.calls, []);
  });

  it("rejects pending requests when the channel closes", async () => {
    const { channel, rpc } = connect();

    const reply = rpc.request("session/prompt", {});
    channel.close();

    await assert.rejects(reply, ChannelClosedError);
  });
});
