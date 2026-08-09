package org.dx.showmymaps.paper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The server half of SHOW MY MAPS for servers that cannot load a Fabric mod.
 *
 * <p>Minecraft sends a filled map's colours in two cases only: the map sits in a
 * player's own inventory, or it hangs in an item frame the player is near. A map in
 * a chest, in an auction or shop GUI, inside a shulker box or lying on the ground is
 * never sent, so a client has nothing to draw however it renders maps. This walks
 * what each player can currently see and sends those maps to them.
 */
public final class ShowMyMapsPlugin extends JavaPlugin implements Listener {
    /**
     * Held by everyone unless an owner takes it away. A server that sells map art,
     * or hides it behind a rank, revokes this and the previews stop: the colours are
     * never sent, so there is nothing on the client to draw.
     */
    public static final String SEE_PERMISSION = "showmymaps.see";

    /**
     * The mod asks whichever server half is present to name itself, so a client can
     * tell "this server sends nothing" apart from "this server is behind". The
     * payload is the version as plain UTF-8, which is what the mod's codec reads.
     */
    public static final String PRESENCE_CHANNEL = "show_my_maps:presence";

    /** Map ids already sent to a player, and when, so the same 16 KB is not resent every pass. */
    private final Map<UUID, Map<Integer, Long>> sent = new HashMap<>();

    private long scanIntervalTicks;
    private long resendMillis;
    private boolean openContainers;
    private boolean shulkerContents;
    private boolean nearbyItems;
    private int nearbyRadius;
    private Set<String> disabledWorlds = Set.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, PRESENCE_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);

        // Per-player tasks, so this stays correct on region threaded forks such as
        // Folia and ShreddedPaper: each pass runs on the thread that owns the player.
        for (Player player : getServer().getOnlinePlayers()) {
            schedule(player);
        }
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, PRESENCE_CHANNEL);
        sent.clear();
    }

    private void readConfig() {
        scanIntervalTicks = Math.max(5, getConfig().getLong("scan-interval-ticks", 20L));
        resendMillis = Math.max(1L, getConfig().getLong("resend-seconds", 60L)) * 1000L;
        openContainers = getConfig().getBoolean("open-containers", true);
        shulkerContents = getConfig().getBoolean("shulker-contents", true);
        nearbyItems = getConfig().getBoolean("nearby-items", true);
        nearbyRadius = Math.max(1, getConfig().getInt("nearby-radius", 12));
        disabledWorlds = getConfig().getStringList("disabled-worlds").stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether this player is owed map colours at all. */
    private boolean allowed(Player player) {
        return player.hasPermission(SEE_PERMISSION)
            && !disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private void schedule(Player player) {
        player.getScheduler().runAtFixedRate(this, task -> sweep(player), null,
            scanIntervalTicks, scanIntervalTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        schedule(player);

        // A moment after joining, once the client has said which channels it takes.
        player.getScheduler().runDelayed(this, task -> announce(player), null, 20L);
    }

    private void announce(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(PRESENCE_CHANNEL)) {
            return;
        }

        player.sendPluginMessage(this, PRESENCE_CHANNEL,
            getPluginMeta().getVersion().getBytes(StandardCharsets.UTF_8));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
    }

    /** A GUI's first page is worth sending before the next scheduled pass. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && allowed(player)) {
            player.getScheduler().runDelayed(this, task -> sweep(player), null, 1L);
        }
    }

    /** Clicking a plugin GUI usually turns the page, which swaps every item in it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && allowed(player)) {
            player.getScheduler().runDelayed(this, task -> sweep(player), null, 2L);
        }
    }

    private void sweep(Player player) {
        if (!player.isOnline() || !allowed(player)) {
            return;
        }

        if (openContainers) {
            Inventory top = player.getOpenInventory().getTopInventory();

            // With nothing else open, the "top" inventory is the player's own 2x2
            // crafting grid. An ender chest's holder is the owning Player too, so
            // that used to read as "nothing open" and skip it; check the inventory
            // type instead, which tells the two apart correctly.
            if (top.getType() != InventoryType.CRAFTING) {
                sendAll(player, top.getContents());
            }

            send(player, player.getOpenInventory().getCursor());
        }

        if (shulkerContents) {
            for (ItemStack stack : player.getInventory().getContents()) {
                sendNested(player, stack);
            }
        }

        if (nearbyItems) {
            for (Entity entity : player.getNearbyEntities(nearbyRadius, nearbyRadius, nearbyRadius)) {
                if (entity instanceof Item item) {
                    send(player, item.getItemStack());
                }
            }
        }
    }

    private void sendAll(Player player, ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            send(player, stack);
            sendNested(player, stack);
        }
    }

    /** Maps packed inside a shulker box or a bundle, which nothing ever syncs. */
    private void sendNested(Player player, ItemStack holder) {
        if (holder == null || holder.getType().isAir()) {
            return;
        }

        // hasItemMeta() is false for a stack whose meta has nothing beyond the
        // material's defaults, which is exactly the common case for a shulker box
        // or bundle: get the meta unconditionally instead of gating on that flag.
        ItemMeta meta = holder.getItemMeta();

        if (meta instanceof BlockStateMeta blockState
            && blockState.hasBlockState()
            && blockState.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack stack : box.getInventory().getContents()) {
                send(player, stack);
            }
        } else if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack stack : bundle.getItems()) {
                send(player, stack);
            }
        }
    }

    private void send(Player player, ItemStack stack) {
        // hasItemMeta() is false for a plain filled map with nothing set beyond the
        // map id itself, which is the common case, so it can't gate this: get the
        // meta unconditionally and let the instanceof check decide.
        if (stack == null || !(stack.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return;
        }

        MapView view = meta.getMapView();

        if (view == null) {
            return;
        }

        Map<Integer, Long> seen = sent.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = seen.get(view.getId());

        if (last != null && now - last < resendMillis) {
            return;
        }

        seen.put(view.getId(), now);
        player.sendMap(view);
    }
}
