package org.dx.showmymaps.paper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.inventory.InventoryView;
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
 * SHOW MY MAPS server half for Bukkit servers. Vanilla only sends colours for maps a player
 * carries or sees in a nearby frame; this sends the maps in whatever they have open or nearby.
 */
public class ShowMyMapsPlugin extends JavaPlugin implements Listener {
    public static final String SEE_PERMISSION = "showmymaps.see";

    // the client reads the version as raw UTF-8, same as the Fabric codec
    public static final String PRESENCE_CHANNEL = "show_my_maps:presence";

    // map id -> when it was last sent; a map is 16 KB on the wire
    private final Map<UUID, Map<Integer, Long>> sent = new HashMap<>();

    private static final int MAX_NESTING = 4;

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

    // counters behind /showmymaps
    private int scheduled;
    private int sweeps;
    private int mapsSent;
    private int unresolved;
    private boolean fellBack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, PRESENCE_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);

        // per-player tasks keep this correct on region-threaded forks (Folia, ShreddedPaper)
        for (Player player : getServer().getOnlinePlayers()) {
            schedule(player);
        }
    }

    /**
     * Tells an owner which half is broken: no sweeps means not running, no maps sent means
     * nothing found or no permission, maps sent but blank means the client is missing the mod.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // with nobody online every counter is zero, which is not a fault
        boolean anyone = !getServer().getOnlinePlayers().isEmpty();

        sender.sendMessage("SHOW MY MAPS " + version());
        sender.sendMessage("  sweeps run:      " + this.sweeps
            + (this.sweeps == 0
                ? (anyone ? "   <- nothing is running; see the console for errors"
                          : "   (nobody online yet, so there is nothing to sweep)")
                : ""));
        sender.sendMessage("  maps sent:       " + this.mapsSent
            + (this.sweeps > 0 && this.mapsSent == 0
                ? "   <- looking, finding none: check showmymaps.see and disabled-worlds" : ""));
        sender.sendMessage("  unresolvable:    " + this.unresolved
            + (this.unresolved > 0
                ? "   <- another plugin shows maps it has no data for; nothing can fix those" : ""));
        sender.sendMessage("  players tracked: " + this.scheduled
            + (this.fellBack ? " (on the main scheduler; per-player was refused)" : ""));

        if (this.mapsSent > 0) {
            sender.sendMessage("  Maps are being sent. Anything still blank is the client:"
                + " install the Fabric mod and keep it on the same version.");
        }

        return true;
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, PRESENCE_CHANNEL);
        sent.clear();
    }

    // getPluginMeta() is Paper-only and throws on Spigot
    @SuppressWarnings("deprecation")
    private String version() {
        return getDescription().getVersion();
    }

    // InventoryView became an interface in 1.21, so a direct call breaks on one side or the other
    private static Inventory topInventory(Player player) {
        Object view = player.getOpenInventory();

        if (view == null) {
            return null;
        }

        try {
            if (getTopInventory == null) {
                getTopInventory = InventoryView.class.getMethod("getTopInventory");
            }

            return (Inventory) getTopInventory.invoke(view);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static volatile Method getTopInventory;

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

    private boolean allowed(Player player) {
        return player.hasPermission(SEE_PERMISSION)
            && !disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    /**
     * Prefers the per-player scheduler; falls back to the global one when a fork lacks it or
     * refuses the task, instead of loading and silently sweeping nothing.
     */
    private void schedule(Player player) {
        try {
            player.getScheduler().runAtFixedRate(this, task -> sweep(player), null,
                scanIntervalTicks, scanIntervalTicks);
            scheduled++;
            return;
        } catch (LinkageError | RuntimeException e) {
            if (!fellBack) {
                fellBack = true;
                getLogger().warning("Per-player scheduler refused a task ("
                    + e.getClass().getSimpleName() + "); using the main scheduler instead");
            }
        }

        getServer().getScheduler().runTaskTimer(this, () -> sweep(player),
            scanIntervalTicks, scanIntervalTicks);
        scheduled++;
    }

    private void soon(Player player, Runnable job, long delayTicks) {
        try {
            player.getScheduler().runDelayed(this, task -> job.run(), null, delayTicks);
            return;
        } catch (LinkageError | RuntimeException e) {
        }

        getServer().getScheduler().runTaskLater(this, job, delayTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        schedule(player);

        // after the client has registered its channels
        soon(player, () -> announce(player), 20L);
    }

    private void announce(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(PRESENCE_CHANNEL)) {
            return;
        }

        player.sendPluginMessage(this, PRESENCE_CHANNEL,
            version().getBytes(StandardCharsets.UTF_8));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
    }

    // the client drops every map when its level is rebuilt, so forget what was sent and resend
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }

    private void forget(Player player) {
        sent.remove(player.getUniqueId());

        if (debug) {
            getLogger().info("[debug] " + player.getName() + " changed level; resending on the next pass");
        }

        soon(player, () -> sweep(player), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && allowed(player)) {
            soon(player, () -> sweep(player), 1L);
        }
    }

    // plugin GUIs usually turn the page on click
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && allowed(player)) {
            soon(player, () -> sweep(player), 2L);
        }
    }

    private void sweep(Player player) {
        if (!player.isOnline() || !allowed(player)) {
            return;
        }

        this.sweeps++;

        if (openContainers) {
            Inventory top = topInventory(player);

            // CRAFTING is the player's own grid; an ender chest's holder is also the player, so check the type
            if (top != null && top.getType() != InventoryType.CRAFTING) {
                sendAll(player, top.getContents());
            }

            // trades live in their own list, not the merchant inventory
            if (merchantTrades && top instanceof MerchantInventory merchant) {
                sendTrades(player, merchant);
            }

            send(player, player.getItemOnCursor());
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

    // vanilla only syncs frame maps as the frame enters tracking range, and never syncs
    // armour stands or display entities
    private void sendCarriedBy(Player player, Entity entity) {
        if (nearbyItems && entity instanceof Item item) {
            send(player, item.getItemStack());
            return;
        }

        if (!nearbyDisplays) {
            return;
        }

        if (entity instanceof ItemFrame frame) {
            // glow frames included
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

    // shulker boxes and bundles, nested; nothing else ever syncs these
    private void sendNested(Player player, ItemStack holder, int depth) {
        if (holder == null || holder.getType().isAir() || depth >= MAX_NESTING) {
            return;
        }

        // hasItemMeta() is false for a default-meta box, so do not gate on it
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
        // hasItemMeta() is false for a plain map, so do not gate on it
        if (stack == null || !(stack.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) {
            return;
        }

        MapView view = meta.getMapView();

        if (view == null) {
            // a plugin drew this map straight to packets; nothing here can resolve it
            this.unresolved++;

            if (debug) {
                getLogger().warning("[debug] stack names a map id with no map data behind it");
            }

            return;
        }

        Map<Integer, Long> seen = sent.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = seen.get(view.getId());

        if (last != null && now - last < resendMillis) {
            return;
        }

        if (seen.size() > PRUNE_ABOVE) {
            seen.values().removeIf(when -> now - when >= resendMillis);
        }

        seen.put(view.getId(), now);
        player.sendMap(view);
        this.mapsSent++;

        if (debug) {
            getLogger().info("[debug] sent map " + view.getId() + " to " + player.getName());
        }
    }
}
