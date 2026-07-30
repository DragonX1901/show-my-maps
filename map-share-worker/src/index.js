/**
 * Map share service for SHOW MY MAPS.
 *
 * A filled map's colours live on the Minecraft server and are sent only to players
 * carrying that map or standing near it in an item frame. There is no packet to ask
 * for one. This holds the cache files of players who were sent a map, so players who
 * were not can fetch the same 16 KB instead of seeing blank parchment.
 *
 *   GET  /<server>/<mapId>.bin  -> 200 with the file, 404 when nobody has shared it
 *   PUT  /<server>/<mapId>.bin  -> 201 stored, 200 when it is already there
 *
 * First write wins. A map id means different pictures on different servers, so the
 * server key is part of the path and files never cross between them.
 */

const MIN_BYTES = 16 * 1024;
const MAX_BYTES = 64 * 1024;
const SERVER_KEY = /^[a-z0-9._-]{1,64}$/;
const MAP_ID = /^\d{1,10}$/;

export default {
  async fetch(request, env) {
    const key = objectKey(new URL(request.url));

    if (!key) {
      return text(404, 'not found');
    }

    if (request.method === 'GET' || request.method === 'HEAD') {
      return read(env, key, request.method === 'HEAD');
    }

    if (request.method === 'PUT') {
      return write(request, env, key);
    }

    return text(405, 'method not allowed', { Allow: 'GET, HEAD, PUT' });
  },
};

/** Reads the two trailing segments, so the service can live under any path prefix. */
function objectKey(url) {
  const parts = url.pathname.split('/').filter(Boolean);

  if (parts.length < 2) {
    return null;
  }

  const server = parts[parts.length - 2].toLowerCase();
  const file = parts[parts.length - 1];

  if (!file.endsWith('.bin')) {
    return null;
  }

  const id = file.slice(0, -'.bin'.length);

  if (!SERVER_KEY.test(server) || !MAP_ID.test(id)) {
    return null;
  }

  return `${server}/${id}.bin`;
}

async function read(env, key, headOnly) {
  const object = await env.MAPS.get(key);

  if (!object) {
    return text(404, 'not shared');
  }

  const headers = {
    'Content-Type': 'application/octet-stream',
    'Content-Length': String(object.size),
    // The bytes for one id never change, so let clients and the edge keep them.
    'Cache-Control': 'public, max-age=86400, immutable',
    ETag: object.httpEtag,
  };

  return new Response(headOnly ? null : object.body, { status: 200, headers });
}

async function write(request, env, key) {
  if (env.SHARE_TOKEN && request.headers.get('X-Share-Token') !== env.SHARE_TOKEN) {
    return text(403, 'bad token');
  }

  const declared = Number(request.headers.get('Content-Length'));

  if (!Number.isFinite(declared) || declared < MIN_BYTES || declared > MAX_BYTES) {
    return text(413, 'unexpected size');
  }

  // First write wins: without this, anyone could overwrite a map with the wrong art.
  if (await env.MAPS.head(key)) {
    return text(200, 'already shared');
  }

  const body = await request.arrayBuffer();

  if (body.byteLength < MIN_BYTES || body.byteLength > MAX_BYTES) {
    return text(413, 'unexpected size');
  }

  if (!looksLikeMapFile(body)) {
    return text(415, 'not a map file');
  }

  await env.MAPS.put(key, body, {
    httpMetadata: { contentType: 'application/octet-stream' },
  });

  return text(201, 'stored');
}

/** The client writes a format int of 1, then scale, then a locked flag. */
function looksLikeMapFile(body) {
  if (body.byteLength < 6) {
    return false;
  }

  const view = new DataView(body);
  const format = view.getInt32(0, false);
  const scale = view.getInt8(4);
  const locked = view.getInt8(5);

  return format === 1 && scale >= 0 && scale <= 4 && (locked === 0 || locked === 1);
}

function text(status, body, headers = {}) {
  return new Response(`${body}\n`, {
    status,
    headers: { 'Content-Type': 'text/plain; charset=utf-8', ...headers },
  });
}
