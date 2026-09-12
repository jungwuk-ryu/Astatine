import net from "node:net";
function sendRconCommand(port, password, command, timeoutMs) {
  return new Promise((resolvePromise, reject) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    let buffer = Buffer.alloc(0);
    let stage = "auth";
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`RCON timeout after ${timeoutMs}ms on ${port}`));
    }, timeoutMs);

    socket.on("connect", () => {
      socket.write(makeRconPacket(1, 3, password));
    });
    socket.on("data", (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      for (;;) {
        const packet = tryReadPacket();
        if (!packet) {
          return;
        }
        if (stage === "auth") {
          if (packet.id === -1) {
            clearTimeout(timer);
            socket.destroy();
            reject(new Error(`RCON authentication failed on ${port}`));
            return;
          }
          stage = "command";
          socket.write(makeRconPacket(2, 2, command));
        } else {
          clearTimeout(timer);
          socket.end();
          resolvePromise(packet.payload);
          return;
        }
      }
    });
    socket.on("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });

    function tryReadPacket() {
      if (buffer.length < 4) {
        return null;
      }
      const length = buffer.readInt32LE(0);
      if (buffer.length < 4 + length) {
        return null;
      }
      const data = buffer.subarray(4, 4 + length);
      buffer = buffer.subarray(4 + length);
      return {
        id: data.readInt32LE(0),
        type: data.readInt32LE(4),
        payload: data.subarray(8, Math.max(8, data.length - 2)).toString("utf8"),
      };
    }
  });
}

function makeRconPacket(id, type, payload) {
  const payloadBytes = Buffer.from(payload, "utf8");
  const packet = Buffer.alloc(14 + payloadBytes.length);
  packet.writeInt32LE(10 + payloadBytes.length, 0);
  packet.writeInt32LE(id, 4);
  packet.writeInt32LE(type, 8);
  payloadBytes.copy(packet, 12);
  return packet;
}


export {sendRconCommand};
if(process.argv[1]?.endsWith("rcon.mjs")) console.log(await sendRconCommand(25686,"local-tracker-benchmark",process.argv.slice(2).join(" "),120000));
