# SHOW MY MAPS

Fabric client mod. Hover a filled map and see the picture in the tooltip, instead of having to
put the map in your hand.

* **Map icons** — the slot itself shows the art instead of the parchment sprite, so a chest or
  auction page of map art reads without touching the mouse. On by default.
* **Dropped maps** — under icon mode, a map lying on the ground spins as the art itself instead
  of a rolled parchment.
* **Shulker box tooltip** — hover a box and its contents appear as a slot grid, maps included,
  replacing vanilla's list of item names.
* **Tooltip preview** — hover a filled map in your inventory, a chest, a hopper, the creative
  menu, anywhere a tooltip shows up. The map renders under the normal tooltip lines.
* Install on client and server. Singleplayer and LAN need nothing extra; on a vanilla server the client half still previews maps the server already sent.

## Config

Everything is in the settings screen, which Mod Menu opens. It is built from vanilla widgets,
so the mod needs no config library. The file is `config/show_my_maps.json`, written when the
screen closes.

| Field | Default | Meaning |
| --- | --- | --- |
| `slotPreview` | `true` | Draw map art in place of the item sprite |
| `slotPreviewSize` | `16` | Icon size in GUI pixels (8–16) |
| `tooltipEnabled` | `true` | Draw the picture in tooltips |
| `tooltipSize` | `128` | Tooltip preview size in GUI pixels (32–256) |
| `containerTooltip` | `true` | Show shulker box contents as a slot grid |
| `cacheMapData` | `true` | Keep received map colours on disk |
| `serverNotice` | `true` | Say so on joining a server that does not run the mod |

## Map cache

A server sends a map's colours once, and the client forgets them when you disconnect or change
dimension. Every map that arrives is written to `show_my_maps_cache/<server>/map_<id>.bin`
(16 KB each) and read back when the server has not sent that map this session. A map you have
seen once — carried, bought, or passed in an item frame — keeps previewing afterwards, including
in a shop or auction GUI that lists the same map later.

A network usually answers to several addresses, and the cache folder is named after the one you
typed. Joining through `play.example.com` today and `eu.example.com` tomorrow would otherwise
split one cache in two, so a map missing from the current folder is looked for in the folders of
other addresses on the same registrable domain, and copied across when it turns up. Bare IPs are
never grouped: unrelated servers must not lend each other map ids.

This cannot conjure maps you have never been sent. There is no serverbound "send me map N"
packet in the protocol; `ClientboundMapItemDataPacket` is one-way, so the server decides.

## Paper plugin

`paper-plugin/` is the server half for Paper, Spigot and their forks, which cannot load a Fabric
mod. It is built against the oldest API in its range so one jar covers 1.21.1 through 26.2, and
every one of those was booted with it before that was claimed. It sends map colours for maps in whatever inventory a player has open — chests, auction and
shop pages, any plugin GUI — plus shulker box contents and maps lying nearby, which is the whole
set vanilla leaves out. Build it with `./gradlew -p paper-plugin build` and drop the jar in
`plugins/`. Settings live in `config.yml`; per-player schedulers keep it correct on Folia and
ShreddedPaper.

Either half announces itself and its version to a joining client, so the mod can tell "this
server sends nothing" apart from "this server is behind" and say which. Anything older than
1.0.3 cannot announce itself and reads as absent.

Owners decide who gets previews. Everyone holds `showmymaps.see` unless it is taken away, so a
server selling map art can revoke it and grant it to a rank, or list worlds under
`disabled-worlds`. A player without it is never sent the colours, so there is nothing on their
client to draw: the switch is server side, not a client setting anyone can flip back.

### Trying it against a real server

`paper-plugin/stub-auction/` is a fixture that stands in for the auction plugins a public
network runs: it opens a GUI holding a filled map the player never carried, which is the case
nothing sends colours for. Put it and a Paper server somewhere, then join with the mod:

```
./gradlew -p paper-plugin/stub-auction build     # the fake auction house
./gradlew -p paper-plugin build                  # the real plugin
./gradlew :1.21.11:runClient -Ptest_server=127.0.0.1:25565
```

The client joins on start up and the GUI opens itself, so nothing needs driving by hand. Without
`ShowMyMaps-Paper` in `plugins/` the listing is a blank parchment and no file appears under
`run/show_my_maps_cache/`; with it the art draws and the file is written. That pair is the whole
claim, and it is worth rerunning rather than believing.

## Why there is a server side

Map pixels live on the server, and vanilla is stingy with them twice over. It paints a map only
while you hold it (`MapItem.inventoryTick` skips anything outside a hand), and it sends colours
only for maps you carry or see in an item frame. A fresh map in your backpack is therefore blank
*and* unsent, which no client mod can paint around.

This mod fixes both on the server side:

* `MapItemCarriedUpdateMixin` paints maps sitting in your inventory, every 4 ticks by default.
* `ContainerMapSync` pushes colours for maps in a chest you have open and for maps packed inside
  a shulker box you carry, painting them while you stand inside the mapped area.

Install it on both sides — singleplayer and LAN cover this on their own.

### On a server that does not run the mod

Most public servers are Paper or Spigot and cannot load a Fabric mod at all, so only the client
half runs. Vanilla still sends colours in two cases: maps in your own inventory
(`ServerPlayer.doTick` calls `synchronizeSpecialItemUpdates` on each inventory slot) and maps in
an item frame you are near (`ServerEntity.sendChanges` pushes those to every tracking player). So
icons and tooltips work for a map you carry or walk past on a wall, and the cache keeps them
working afterwards. A map you have seen neither way — in a chest, inside a shulker box, lying on
the ground, held by someone else — has no colours on the client and reads *This server never sent
this map*.

The client tells the two cases apart. The server side registers a `show_my_maps:presence`
channel, so a second after joining the client knows whether the other end runs the mod, and says
once in chat when it does not. Turn that off with `serverNotice`. A proxy in front of the server
(Velocity, BungeeCord) or a protocol bridge like ViaVersion changes nothing here: the check reads
the channel list the backend itself advertises.

| Field in `config/show_my_maps_server.json` | Default | Meaning |
| --- | --- | --- |
| `paintCarriedMaps` | `true` | Paint inventory maps, not only held ones |
| `carriedPaintInterval` | `4` | Ticks between painting passes |
| `syncContainerMaps` | `true` | Send colours for maps in an open container |
| `paintContainerMaps` | `true` | Paint those maps too |
| `containerSyncInterval` | `10` | Ticks between container passes |

The client's colour cache lives in its level object, so it clears when you change dimension or
reconnect, then refills.

## Versions

Four jars, one source tree. [Stonecutter](https://stonecutter.kikugie.dev/) keeps the version
differences in comment blocks and builds each target from `versions/<game version>/`.

| Jar | Covers | Notes |
| --- | --- | --- |
| `1.0.0+1.21.1` | 1.21, 1.21.1 | Older map renderer: `PoseStack` and `MultiBufferSource`, HUD through `HudRenderCallback` |
| `1.0.0+1.21.11` | 1.21.9, 1.21.10, 1.21.11 | Render-state architecture; the version the tests run on |
| `1.0.0+26.1.2` | 26.1, 26.1.1, 26.1.2 | Unobfuscated game, Java 25, `GuiGraphics` renamed to `GuiGraphicsExtractor` |
| `1.0.0+26.2` | 26.2 | As above, plus the HUD hide flag and screen swap moved again |

The 1.21.11 jar covers three releases because Loom remaps to intermediary names, which do not
change across them, so the Mojmap rename of `ResourceLocation` to `Identifier` in 1.21.11 is a
compile-time detail only. What that claim rests on:

* Every mixin target — `Item.getTooltipImage`, the private `GuiGraphics.renderItem`,
  `GuiGraphics.submitMapRenderState`, `ItemEntityRenderer.submit` / `extractRenderState`,
  `ClientPacketListener.handleMapItemData`, `ItemContainerContents.addToTooltip` — exists with
  the same descriptor in all three versions.
* The built jar launches to the title screen on 1.21.9 and 1.21.10 with no mixin failures.
* Full in-game behaviour (`runClientGameTest`) is exercised on 1.21.11.

The 1.21.1, 26.1.2 and 26.2 jars are verified by building and launching a client to the title screen
with no mixin failures. The client gametest API only exists from 1.21.9 on, so the in-game
tests cannot run on 1.21.1, and they run against the shared code that all three jars compile.

### Building a specific version

```
./gradlew ":1.21.1:build"       # or 1.21.11, 26.1.2, 26.2
./gradlew build                 # all four
./gradlew "Set active project to 1.21.1"   # what the IDE and src/ follow
```

The 26.x targets need Gradle itself running on Java 25, because its Loom alpha requires it:

```
JAVA_HOME=/path/to/jdk-25 ./gradlew ":26.2:build"
```

## Logo

`logo.png` is half vanilla, half this mod: the left side is Minecraft's own `filled_map` item
sprite — a blank parchment, all you get without the mod — and the right side is the same map
rendered by the mod. `LogoShotTest` regenerates the right half: it paints the art into a map,
hovers the slot, and screenshots the tooltip preview at full size.

## Build

```
./gradlew build
```

Jars land in `versions/<game version>/build/libs/`. Use the plain
`SHOW_MY_MAPS-<version>.jar`, not `-sources`.

## License

MIT, see [LICENSE](LICENSE).
