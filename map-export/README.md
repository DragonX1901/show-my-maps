# SHOW MY MAPS — map export

Publishes a world's filled maps so clients can fetch the pictures the server never sends them.

Minecraft transmits a map's colours only while it sits in a player's own inventory or hangs in an
item frame they are near. A map in an auction page or a shop GUI is never sent to anybody, so a
client has nothing to draw. The [Paper plugin](../paper-plugin) fixes that from inside the server.
This fixes it from outside: the same colours are already on disk in `world/data/map_*.dat`, and
copying them to a web host lets every player read them.

Nothing here runs on the server. It needs no plugin, no restart, and no open port — just read
access to the world folder or a backup of it.

## Use

```
java -jar ShowMyMaps-Export.jar <world> <output>
```

- `<world>` — a world folder, or a `.zip` backup of one. The whole tree is searched, so pointing
  it at a Paper server root that splits `world`, `world_nether` and `world_the_end` is fine.
- `<output>` — where to write the results.

```
$ java -jar ShowMyMaps-Export.jar backups/world-2026-08-11.zip public/maps
Exported 1463 map(s) to public/maps
```

A map file it cannot read is reported and skipped, so one corrupt map does not cost you the rest
of the world.

## Output

```
public/maps/
  7.bin
  12.bin
  ...
  manifest.json
```

- `<id>.bin` — the map's scale, locked flag, dimension and its 16384 colours, in the layout the
  mod's own cache uses.
- `manifest.json` — `{"maps": {"<id>": "<sha256 of the colours>"}}`. Optional but worth serving:
  it lets a client skip asking for ids you never published, and catches a truncated or swapped
  file in transit.

Copy the folder to any static host and serve it over HTTPS. Players then set that address as the
**Art source** for your server in the mod's settings and turn on **Fetch missing art**.

## What this is not

The digests are yours, from your host, so they say nothing about whether your host is honest —
they check the transfer, not the source. Clients treat everything fetched as a guess: it is
marked as such on disk, **Strict previews** refuses it, and if the server later sends the same map
for real the two are compared. A source that gets locked maps wrong is switched off on the client.

That is the right way round. A published folder is a convenience an owner offers, not an authority
the game has to believe.

## Building

```
./gradlew jar     # build/libs/ShowMyMaps-Export-<version>.jar
./gradlew test
```

Java 21 or newer, no dependencies — the NBT reader is in `Nbt.java`, at about 150 lines, so the
tool runs anywhere a JRE does.
