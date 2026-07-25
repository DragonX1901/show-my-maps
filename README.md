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
* **HUD widget** — keeps one carried map on screen while it stays in your inventory. Off by
  default; press `M` to turn it on.
* Install on client and server. Singleplayer and LAN need nothing extra; on a vanilla server the client half still previews maps the server already sent.

## Keys

| Key | Action |
| --- | --- |
| `M` | Toggle the HUD widget |

Rebind under Controls → Miscellaneous. Settings also live in Mod Menu, if you have it —
the mod builds its screen from vanilla widgets, so no config library is needed.

## Config

`config/show_my_maps.json`, written when you toggle a key.

| Field | Default | Meaning |
| --- | --- | --- |
| `slotPreview` | `true` | Draw map art in place of the item sprite |
| `slotPreviewSize` | `16` | Icon size in GUI pixels (8–16) |
| `tooltipEnabled` | `true` | Draw the picture in tooltips |
| `tooltipSize` | `128` | Tooltip preview size in GUI pixels (32–256) |
| `hudEnabled` | `false` | Draw the HUD widget |
| `hudSize` | `64` | HUD widget size in GUI pixels (32–256) |
| `hudOffsetX` / `hudOffsetY` | `4` | Gap from the top right corner |
| `containerTooltip` | `true` | Show shulker box contents as a slot grid |
| `cacheMapData` | `true` | Keep received map colours on disk |

## Map cache

A server sends a map's colours once, and the client forgets them when you disconnect or change
dimension. Every map that arrives is written to `show_my_maps_cache/<server>/map_<id>.bin`
(16 KB each) and read back when the server has not sent that map this session. A map you have
seen once — carried, bought, or passed in an item frame — keeps previewing afterwards, including
in a shop or auction GUI that lists the same map later.

This cannot conjure maps you have never been sent. There is no serverbound "send me map N"
packet in the protocol; `ClientboundMapItemDataPacket` is one-way, so the server decides.

## Why there is a server side

Map pixels live on the server, and vanilla is stingy with them twice over. It paints a map only
while you hold it (`MapItem.inventoryTick` skips anything outside a hand), and it sends colours
only for maps you carry or see in an item frame. A fresh map in your backpack is therefore blank
*and* unsent, which no client mod can paint around.

This mod fixes both on the server side:

* `MapItemCarriedUpdateMixin` paints maps sitting in your inventory, every 4 ticks by default.
* `ContainerMapSync` pushes colours for maps in a chest you have open and for maps packed inside
  a shulker box you carry, painting them while you stand inside the mapped area.

Install it on both sides — singleplayer and LAN cover this on their own. On a vanilla or Paper
server without the mod, the client half still runs and previews any map the server already sent
you; everything else reads *Map data not received yet*.

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

One jar covers **1.21.9, 1.21.10 and 1.21.11**. It is compiled against 1.21.11 and remapped to
intermediary names, which do not change across those releases, so the Mojmap rename of
`ResourceLocation` to `Identifier` in 1.21.11 is a compile-time detail only.

What was checked, so you know what the support claim rests on:

* Every mixin target — `Item.getTooltipImage`, the private `GuiGraphics.renderItem`,
  `GuiGraphics.submitMapRenderState`, `ItemEntityRenderer.submit` / `extractRenderState`,
  `ClientPacketListener.handleMapItemData`, `ItemContainerContents.addToTooltip` — exists with
  the same descriptor in all three versions.
* The built jar launches to the title screen on 1.21.9 and 1.21.10 with no mixin failures.
* Full in-game behaviour (`runClientGameTest`) is exercised on 1.21.11.

Older 1.21 releases need a separate branch: the map renderer took `PoseStack` and
`MultiBufferSource` before the render-state rework. 26.x needs its own build too — it wants
Java 25, and Loom 1.17 cannot resolve official mappings for it yet.

## Logo

`logo.png` is half vanilla, half this mod: the left side is Minecraft's own `filled_map` item
sprite — a blank parchment, all you get without the mod — and the right side is the same map
rendered by the mod. `LogoShotTest` regenerates the right half: it lays coloured wool in rings
around the player, waits for the map to paint, and screenshots the HUD widget.

## Build

```
./gradlew build
```

Jar lands in `build/libs/`. Use the plain `SHOW_MY_MAPS-<version>.jar`, not `-sources`.
