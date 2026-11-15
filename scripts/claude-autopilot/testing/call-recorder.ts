/**
 * A function that remembers each call's arguments and answers with a fixed reply, standing in for a
 * callback the code under test is handed.
 *
 * @example
 * const onFrame = CallRecorder.silent<[]>();
 * await subject.run({ onFrame: onFrame.fn });
 * assert.equal(onFrame.calls.length, 1);
 */
export class CallRecorder<Args extends unknown[], Reply> {
  readonly calls: Args[] = [];

  private constructor(private readonly reply: () => Reply) {}

  /** Calls the code under test makes and nobody answers. */
  static silent<Args extends unknown[]>(): CallRecorder<Args, void> {
    return new CallRecorder<Args, void>(() => undefined);
  }

  static resolving<Args extends unknown[], Value>(
    value: Value,
  ): CallRecorder<Args, Promise<Value>> {
    return new CallRecorder<Args, Promise<Value>>(() => Promise.resolve(value));
  }

  static rejecting<Args extends unknown[]>(
    error: Error,
  ): CallRecorder<Args, Promise<never>> {
    return new CallRecorder<Args, Promise<never>>(() => Promise.reject(error));
  }

  readonly fn = (...args: Args): Reply => {
    this.calls.push(args);
    return this.reply();
  };
}
