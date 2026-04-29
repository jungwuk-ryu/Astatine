import assert from "node:assert/strict";
import test from "node:test";
import {
  buildJvmArgs,
  buildMetadata,
  buildServerCommand,
  parseArgs,
  quoteArg,
  resolveJavaBin,
} from "./java25-network-profile.mjs";

test("compact-object-headers profile adds the Java 25 product flag only", () => {
  const options = parseArgs(["--profile", "compact-object-headers", "--heap", "4G"]);
  const args = buildJvmArgs(options);

  assert.ok(args.includes("-XX:+UseCompactObjectHeaders"));
  assert.equal(args.includes("-XX:+UnlockExperimentalVMOptions"), false);
  assert.ok(args.includes("-Xms4G"));
  assert.ok(args.includes("-Xmx4G"));
});

test("baseline profile keeps compact object headers disabled", () => {
  const options = parseArgs(["--profile", "baseline", "--gc", "none"]);
  const args = buildJvmArgs(options);

  assert.equal(args.includes("-XX:+UseCompactObjectHeaders"), false);
  assert.equal(args.some(arg => arg.startsWith("-XX:+UseG1GC")), false);
});

test("direct memory and repeated extra args are preserved in command order", () => {
  const options = parseArgs([
    "--profile",
    "compact-object-headers",
    "--direct-memory",
    "2G",
    "--extra-arg",
    "-Dio.netty.allocator.type=pooled",
    "--extra-arg",
    "-Dshreddedpaper.test=true",
    "--jar",
    "server.jar",
  ]);

  assert.deepEqual(buildServerCommand("java25", options).slice(-5), [
    "-Dio.netty.allocator.type=pooled",
    "-Dshreddedpaper.test=true",
    "-jar",
    "server.jar",
    "nogui",
  ]);
  assert.ok(buildJvmArgs(options).includes("-XX:MaxDirectMemorySize=2G"));
});

test("extra args can pass JVM options that begin with double dash", () => {
  const options = parseArgs([
    "--extra-arg",
    "--enable-preview",
    "--extra-arg",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
  ]);

  assert.ok(buildJvmArgs(options).includes("--enable-preview"));
  assert.ok(buildJvmArgs(options).includes("--add-opens=java.base/java.lang=ALL-UNNAMED"));
});

test("compact object header flags are owned by the selected profile", () => {
  assert.throws(
    () => parseArgs(["--profile", "compact-object-headers", "--extra-arg", "-XX:-UseCompactObjectHeaders"]),
    /--profile owns/
  );
  assert.throws(
    () => parseArgs(["--profile", "baseline", "--extra-arg", "-XX:+UseCompactObjectHeaders"]),
    /--profile owns/
  );
});

test("metadata records profile, JVM flags, and benchmark dimensions", () => {
  const options = parseArgs([
    "--profile",
    "compact-object-headers",
    "--scenario",
    "boundary-torture",
    "--repeat",
    "3",
  ]);
  const metadata = buildMetadata(options, "/jdk-25/bin/java", "openjdk version \"25.0.1\"", "2026-04-29T00:00:00.000Z");

  assert.equal(metadata.profile, "compact-object-headers");
  assert.equal(metadata.compactObjectHeaders, "enabled");
  assert.equal(metadata.benchmark.scenario, "boundary-torture");
  assert.equal(metadata.benchmark.repeat, 3);
  assert.ok(metadata.jvm.flags.includes("-XX:+UseCompactObjectHeaders"));
});

test("java resolution prefers explicit path, JAVA, then JAVA_HOME", () => {
  assert.equal(resolveJavaBin("/jdk/bin/java", {}), "/jdk/bin/java");
  assert.equal(resolveJavaBin(null, { JAVA: "/custom/java" }), "/custom/java");
  assert.match(resolveJavaBin(null, { JAVA_HOME: "/jdk-25" }), /jdk-25.*bin.*java/);
  assert.equal(resolveJavaBin(null, {}), "java");
});

test("parser rejects unsafe profile and repeat values", () => {
  assert.throws(() => parseArgs(["--profile", "unknown"]), /--profile/);
  assert.throws(() => parseArgs(["--repeat", "0"]), /positive integer/);
});

test("quoteArg keeps simple flags readable and quotes spaced paths", () => {
  assert.equal(quoteArg("-XX:+UseCompactObjectHeaders"), "-XX:+UseCompactObjectHeaders");
  assert.equal(quoteArg("/path with space/java"), "'/path with space/java'");
});
