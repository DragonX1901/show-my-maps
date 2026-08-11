package org.dx.showmymaps.paper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
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
public class ShowMyMapsPlugin extends JavaPlugin implements Listener {
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

    /** A shulker inside a shulker is legal. A tower of them is somebody being clever. */
    private static final int MAX_NESTING = 4;

    /** How many ids one player's record may hold before stale ones are swept out. */
    private static final int PRUNE_ABOVE = 512;

    private long scanIntervalTicks;
    private long resendMillis;
    private boolean openContainers;
    private boolean shulkerContents;
    private boolean nearbyItems;
    private boolean nearbyDisplays;
    private boolean merchantTrades;
    private int nearbyRadius;
    private boolean debug;
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
        nearbyDisplays = getConfig().getBoolean("nearby-displays", true);
        merchantTrades = getConfig().getBoolean("merchant-trades", true);
        nearbyRadius = Math.max(1, getConfig().getInt("nearby-radius", 12));
        debug = getConfig().getBoolean("debug", false);
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

    /**
     * A client throws away every map it knows when its level is rebuilt, which is
     * what a dimension change is. Remembering that we already sent those maps means
     * not sending them again for a minute, and for that minute the player sees blank
     * parchment for maps they were looking at seconds earlier. Forget and resend.
     */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    /** A respawn rebuilds the level the same way, so it loses the same maps. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }

    private void forget(Player player) {
        sent.remove(player.getUniqueId());

        if (debug) {
            getLogger().info("[debug] " + player.getName() + " changed level; resending on the next pass");
        }

        // Far enough after the switch that the client has its new level to put them in.
        player.getScheduler().runDelayed(this, task -> sweep(player), null, 20L);
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

            // A merchant's trades are not in its inventory: the offers are their own
            // list, drawn by the client from the trade packet. A villager shop that
            // sells map art shows nothing without this.
            if (merchantTrades && top instanceof MerchantInventory merchant) {
                sendTrades(player, merchant);
            }

            send(player, player.getOpenInventory().getCursor());
        }

        if (shulkerContents) {
            for (ItemStack stack : player.getInventory().getContents()) {
                sendNested(player, stack, 0);
            }
        }

        if (nearbyItems || nearbyDisplays) {
            for (Entity entity : player.getNearbyEntities(nearbyRadius, nearbyRadius, nearbyRadius)) {
                sendCarriedBy(player, entity);
            }
        }
    }

    /**
     * Maps an entity is showing rather than holding. Vanilla syncs a map in an item
     * frame only as the frame enters tracking range, so a wall the player was already
     * standing at, or one built while they watched, can leave gaps. Armour stands and
     * display entities are never synced at all, and both are ordinary ways to build a
     * shop front.
     */
    private void sendCarriedBy(Player player, Entity entity) {
        if (nearbyItems && entity instanceof Item item) {
            send(player, item.getItemStack());
            return;
        }

        if (!nearbyDisplays) {
            return;
        }

        if (entity instanceof ItemFrame frame) {
            // Covers glow frames too, which are the same class.
            send(player, frame.getItem());
        } else if (entity instanceof ItemDisplay display) {
            send(player, display.getItemStack());
        } else if (entity instanceof ArmorStand stand) {
            EntityEquipment equipment = stand.getEquipment();

            if (equipment != null) {
                for (ItemStack stack : equipment.getArmorContents()) {
                    send(player, stack);
                }

                send(player, equipment.getItemInMainHand());
                send(player, equipment.getItemInOffHand());
            }
        }
    }

    private void sendTrades(Player player, MerchantInventory merchant) {
        for (MerchantRecipe recipe : merchant.getMerchant().getRecipes()) {
            send(player, recipe.getResult());

            for (ItemStack ingredient : recipe.getIngredients()) {
                send(player, ingredient);
            }
        }
    }

    private void sendAll(Player player, ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            send(player, stack);
            sendNested(player, stack, 0);
        }
    }

    /**
     * Maps packed inside a shulker box or a bundle, which nothing ever syncs. Recurses,
     * because a bundle of maps inside a shulker box is a normal way to carry art and
     * one level of unpacking used to stop at the box.
     */
    private void sendNested(Player player, ItemStack holder, int depth) {
        if (holder == null || holder.getType().isAir() || depth >= MAX_NESTING) {
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
                sendNested(player, stack, depth + 1);
            }
        } else if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack stack : bundle.getItems()) {
                send(player, stack);
                sendNested(player, stack, depth + 1);
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
            // The stack names a map this server has no data for. A plugin drawing its
            // own maps straight to packets does this, and nothing here can help - but
            // it is the one silent failure worth being able to see.
            if (debug) {
                getLogger().warning("[debug] a stack carries a map id this server cannot resolve;"
                    + " whichever plugin made it is not backing it with map data");
            }

            return;
        }

        Map<Integer, Long> seen = sent.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = seen.get(view.getId());

        if (last != null && now - last < resendMillis) {
            return;
        }

        // Without this the record grows for as long as a player stays connected, and
        // on a shop server that is thousands of ids nobody will look at again.
        if (seen.size() > PRUNE_ABOVE) {
            seen.values().removeIf(when -> now - when >= resendMillis);
        }

        seen.put(view.getId(), now);
        player.sendMap(view);

        if (debug) {
            getLogger().info("[debug] sent map " + view.getId() + " to " + player.getName());
        }
    }
}
