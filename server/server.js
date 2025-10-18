const http = require("http");
const { WebSocketServer } = require("ws");
const os = require("os");

const PORT = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  if (req.url === "/") {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("OK\n");
  } else {
    res.writeHead(404);
    res.end();
  }
});

const wss = new WebSocketServer({ server, path: "/ws" });

const rooms = new Map();

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}

function getRoom(ws) {
  return ws.roomId ? rooms.get(ws.roomId) : undefined;
}

function othersInRoom(ws) {
  const r = getRoom(ws);
  if (!r) return [];
  return [...r.peers].filter((p) => p !== ws && p.readyState === p.OPEN);
}

function isValidRole(role) {
  return role === "sender" || role === "viewer";
}

function getLanIPv4s() {
  const nets = os.networkInterfaces();
  const addrs = [];
  for (const [name, infos] of Object.entries(nets)) {
    for (const info of infos || []) {
      const family = typeof info.family === "string" ? info.family : (info.family === 4 ? "IPv4" : "IPv6");
      if (family !== "IPv4") continue;
      if (info.internal) continue;
      const isPrivate =
        /^10\./.test(info.address) ||
        /^192\.168\./.test(info.address) ||
        /^172\.(1[6-9]|2\d|3[0-1])\./.test(info.address);
      addrs.push({ name, address: info.address, isPrivate });
    }
  }
  addrs.sort((a, b) => Number(!a.isPrivate) - Number(!b.isPrivate));
  return addrs;
}

function joinRoom(ws, roomId, role) {
  if (!roomId || typeof roomId !== "string") {
    send(ws, { type: "error", reason: "bad-room-id" });
    return;
  }
  role = String(role || "").toLowerCase();
  if (!isValidRole(role)) {
    send(ws, { type: "error", reason: "bad-role" });
    return;
  }

  let room = rooms.get(roomId);
  if (!room) {
    room = { peers: new Set(), roles: {} };
    rooms.set(roomId, room);
  }

  if (room.peers.size >= 2) {
    send(ws, { type: "error", reason: "This room already has two devices." });
    return;
  }

  if (room.roles[role]) {
    send(ws, { type: "error", reason: "There is already $role in the room.", role });
    return;
  }

  ws.roomId = roomId;
  ws.role = role;
  room.peers.add(ws);
  room.roles[role] = ws;

  send(ws, { type: "joined", room: roomId, peers: room.peers.size });

  if (room.roles.sender && room.roles.viewer) {
    for (const peer of room.peers) {
      send(peer, { type: "ready", room: roomId });
    }
  }
}

function leaveRoom(ws) {
  const roomId = ws.roomId;
  if (!roomId) return;

  const room = rooms.get(roomId);
  if (!room) {
    ws.roomId = undefined;
    ws.role = undefined;
    return;
  }

  room.peers.delete(ws);

  if (ws.role && room.roles[ws.role] === ws) {
    delete room.roles[ws.role];
  }

  ws.roomId = undefined;
  ws.role = undefined;

  for (const peer of room.peers) {
    send(peer, { type: "peer-left" });
  }

  if (room.peers.size === 0) rooms.delete(roomId);
}

wss.on("connection", (ws) => {
  ws.isAlive = true;
  ws.on("pong", () => (ws.isAlive = true));

  ws.on("message", (buf) => {
    let msg;
    try {
      msg = JSON.parse(buf.toString());
    } catch {
      send(ws, { type: "error", reason: "invalid-json" });
      return;
    }

    switch (msg.type) {
      case "join": {
        const room = String(msg.room || "");
        const role = String(msg.role || "");
        joinRoom(ws, room, role);
        break;
      }
      case "offer":
      case "answer":
      case "ice": {
        if (!ws.roomId) {
          send(ws, { type: "error", reason: "not-in-room" });
          return;
        }
        for (const peer of othersInRoom(ws)) {
          send(peer, { ...msg });
        }
        break;
      }
      case "leave": {
        leaveRoom(ws);
        break;
      }
      default:
        send(ws, { type: "error", reason: "unknown-type" });
    }
  });

  ws.on("close", () => leaveRoom(ws));
  ws.on("error", () => leaveRoom(ws));
});

const interval = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);

wss.on("close", () => clearInterval(interval));

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Signaling server running (listening on all interfaces)`);
  const ips = getLanIPv4s();
  if (ips.length === 0) {
    console.log(`No LAN IPv4 found. Try: ws://localhost:${PORT}/ws`);
  } else {
    console.log(`WebSocket endpoints:`);
    for (const { name, address } of ips) {
      console.log(`  • ws://${address}:${PORT}/ws   (iface: ${name})`);
    }
  }
});
