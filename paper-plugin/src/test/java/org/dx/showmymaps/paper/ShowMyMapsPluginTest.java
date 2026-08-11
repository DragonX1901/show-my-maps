package org.dx.showmymaps.paper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Regression tests for two real gaps: an open ender chest that read as "nothing
 * open" because its Bukkit holder is the owning player, and a map whose
 * hasItemMeta() lies and says false even though real MapMeta with a real view
 * is sitting right there.
 */
class ShowMyMapsPluginTest {
    private ServerMock server;
    private ShowMyMapsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ShowMyMapsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enderChestContentsAreSynced() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);
        ItemStack mapStack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapStack.getItemMeta();
        meta.setMapView(view);
        mapStack.setItemMeta(meta);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(ShowMyMapsPlugin.SEE_PERMISSION)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        // An ender chest's Bukkit holder is the player opening it, exactly like the
        // player's own crafting grid. Only the inventory type tells them apart.
        Inventory enderChest = mock(Inventory.class);
        when(enderChest.getType()).thenReturn(InventoryType.ENDER_CHEST);
        when(enderChest.getHolder()).thenReturn(player);
        when(enderChest.getContents()).thenReturn(new ItemStack[] {mapStack});

        InventoryView openView = mock(InventoryView.class);
        when(openView.getTopInventory()).thenReturn(enderChest);
        when(player.getOpenInventory()).thenReturn(openView);

        sweep(player);

        verify(player).sendMap(view);
    }

    @Test
    void mapIsSyncedEvenWhenHasItemMetaIsFalse() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);

        MapMeta meta = mock(MapMeta.class);
        when(meta.hasMapView()).thenReturn(true);
        when(meta.getMapView()).thenReturn(view);

        // A plain filled map with nothing set beyond the map id reports no meta at
        // all, even though getItemMeta() still hands back a real MapMeta.
        ItemStack stack = mock(ItemStack.class);
        when(stack.hasItemMeta()).thenReturn(false);
        when(stack.getItemMeta()).thenReturn(meta);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        send(player, stack);

        verify(player).sendMap(view);
    }

    @Test
    void twoPlayersEachGetMapsOnlyForTheirOwnOpenContainer() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);
        ItemStack mapStack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapStack.getItemMeta();
        meta.setMapView(view);
        mapStack.setItemMeta(meta);

        // One chest, one map neither player owns, exactly the auction-listing case.
        Inventory chest = mock(Inventory.class);
        when(chest.getType()).thenReturn(InventoryType.CHEST);
        when(chest.getContents()).thenReturn(new ItemStack[] {mapStack});

        Player alice = viewer(world);
        Player bob = viewer(world);

        // Alice has the chest open; Bob has nothing but his own crafting grid.
        open(alice, chest);
        Inventory bobCrafting = mock(Inventory.class);
        when(bobCrafting.getType()).thenReturn(InventoryType.CRAFTING);
        open(bob, bobCrafting);

        sweep(alice);
        sweep(bob);

        // The map goes to the one looking at it, and to no one else.
        verify(alice).sendMap(view);
        verify(bob, never()).sendMap(any());

        // Bob opens the same chest: now he is owed it too, independently of Alice.
        open(bob, chest);
        sweep(bob);

        verify(bob).sendMap(view);
    }

    /**
     * The gap that made maps go blank after a portal. A client throws away every map
     * it knows when its level is rebuilt, but the record of what had been sent lived
     * until the player quit, so for a whole resend window nothing was sent again and
     * the player saw blank parchment for art they had been looking at seconds before.
     */
    @Test
    void mapsAreResentAfterADimensionChange() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);

        Player player = viewer(world);
        open(player, chestHolding(mapStack(view)));

        sweep(player);
        verify(player).sendMap(view);

        // Nothing has changed, so the resend window still applies.
        sweep(player);
        verify(player, times(1)).sendMap(view);

        World nether = server.addSimpleWorld("world_nether");
        plugin.onChangedWorld(new PlayerChangedWorldEvent(player, nether));

        sweep(player);
        verify(player, times(2)).sendMap(view);
    }

    /** An art wall is item frames, and nothing here used to look at one. */
    @Test
    void mapsInItemFramesNearbyAreSent() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(mapStack(view));

        Player player = viewer(world);
        open(player, crafting());
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.<org.bukkit.entity.Entity>of(frame));

        sweep(player);

        verify(player).sendMap(view);
    }

    /**
     * A merchant's offers are not in its inventory - they are their own list - so a
     * villager selling map art showed nothing however long you stared at it.
     */
    @Test
    void mapsOfferedInMerchantTradesAreSent() throws Exception {
        World world = server.addSimpleWorld("world");
        MapView view = server.createMap(world);

        MerchantRecipe recipe = new MerchantRecipe(mapStack(view), 99);
        Merchant merchant = mock(Merchant.class);
        when(merchant.getRecipes()).thenReturn(List.of(recipe));

        MerchantInventory trades = mock(MerchantInventory.class);
        when(trades.getType()).thenReturn(InventoryType.MERCHANT);
        when(trades.getContents()).thenReturn(new ItemStack[0]);
        when(trades.getMerchant()).thenReturn(merchant);

        Player player = viewer(world);
        open(player, trades);

        sweep(player);

        verify(player).sendMap(view);
    }

    private static ItemStack mapStack(MapView view) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        meta.setMapView(view);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Inventory chestHolding(ItemStack stack) {
        Inventory chest = mock(Inventory.class);
        when(chest.getType()).thenReturn(InventoryType.CHEST);
        when(chest.getContents()).thenReturn(new ItemStack[] {stack});
        return chest;
    }

    private static Inventory crafting() {
        Inventory grid = mock(Inventory.class);
        when(grid.getType()).thenReturn(InventoryType.CRAFTING);
        return grid;
    }

    /** A player who is allowed map colours and carries nothing of their own. */
    private Player viewer(World world) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(ShowMyMapsPlugin.SEE_PERMISSION)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        // Forgetting a player's record queues a sweep on their own scheduler.
        when(player.getScheduler()).thenReturn(mock(EntityScheduler.class));
        return player;
    }

    private void open(Player player, Inventory top) {
        InventoryView openView = mock(InventoryView.class);
        when(openView.getTopInventory()).thenReturn(top);
        when(player.getOpenInventory()).thenReturn(openView);
    }

    private void sweep(Player player) throws ReflectiveOperationException {
        Method method = ShowMyMapsPlugin.class.getDeclaredMethod("sweep", Player.class);
        method.setAccessible(true);
        method.invoke(plugin, player);
    }

    private void send(Player player, ItemStack stack) throws ReflectiveOperationException {
        Method method = ShowMyMapsPlugin.class.getDeclaredMethod("send", Player.class, ItemStack.class);
        method.setAccessible(true);
        method.invoke(plugin, player, stack);
    }
}
