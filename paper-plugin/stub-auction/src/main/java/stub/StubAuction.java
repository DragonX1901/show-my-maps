package stub;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stands in for the auction plugins on a real network: a paginated GUI full of filled
 * maps the viewer has never carried and never will. Exactly the case a vanilla server
 * never sends colours for.
 *
 * <p>Deliberately awkward in the three ways a real auction house is, because each one
 * is somewhere a fix can quietly fail:
 *
 * <ul>
 *   <li>a whole double chest of maps at once, not one, so the sweep has to carry
 *       54 lots of 16 KB in a pass rather than a comfortable single map;</li>
 *   <li>contents filled a tick after the inventory opens, the way a plugin that
 *       queries a database does, so anything reading on the open event alone sees
 *       an empty box;</li>
 *   <li>every item swapped on click, which is what turning a page does.</li>
 * </ul>
 */
public final class StubAuction extends JavaPlugin implements Listener {
    /** A full double chest, which is what "/ah search filled_map" looks like. */
    private static final int PAGE_SIZE = 54;

    /** Two pages of distinct art, so turning the page is a real change of contents. */
    private static final int PAGES = 2;

    private final List<List<ItemStack>> pages = new ArrayList<>();

    /** How many times the page has turned by itself, so a run covers both pages. */
    private int turned;

    @Override
    public void onEnable() {
        for (int page = 0; page < PAGES; page++) {
            List<ItemStack> listings = new ArrayList<>();

            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                listings.add(listing(page, slot));
            }

            pages.add(listings);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Stub auction built " + (PAGES * PAGE_SIZE) + " listings across "
            + PAGES + " pages; first map id " + idOf(pages.get(0).get(0)));

        // Keeps a GUI up whatever the world does to the player, so a run cannot end
        // up measuring a closed screen.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getSize() != PAGE_SIZE) {
                    open(player, 0);
                }
            }
        }, 100L, 60L);

        // Turns the page on its own every ten seconds, so an unattended run still
        // exercises the case a search result hits: every item in the box replaced
        // while it stays open, with no reopen to hang a fresh sweep off.
        getServer().getScheduler().runTaskTimer(this, () -> {
            this.turned++;

            for (Player player : getServer().getOnlinePlayers()) {
                fill(player, this.turned);
            }
        }, 400L, 200L);
    }

    private ItemStack listing(int page, int slot) {
        MapView view = Bukkit.createMap(getServer().getWorlds().get(0));
        view.getRenderers().clear();
        view.addRenderer(new Blocks(page * PAGE_SIZE + slot));
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);

        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("Listing " + page + "-" + slot);
        stack.setItemMeta(meta);
        return stack;
    }

    private static int idOf(ItemStack stack) {
        MapMeta meta = (MapMeta) stack.getItemMeta();
        MapView view = meta == null ? null : meta.getMapView();
        return view == null ? -1 : view.getId();
    }

    /** Every id this stub is showing, so a test can check them one by one. */
    public List<Integer> mapIds(int page) {
        return pages.get(page).stream().map(StubAuction::idOf).toList();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                open(player, 0);
            }
        }, 100L);
    }

    /** Turning a page swaps every item, which is the case a one-shot sweep misses. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getSize() != PAGE_SIZE) {
            return;
        }

        event.setCancelled(true);
        getServer().getScheduler().runTaskLater(this, () -> fill(player, 1), 1L);
    }

    private void open(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, PAGE_SIZE, "Auction");
        player.openInventory(gui);
        // A real auction house queries a database first, so the box is empty for a
        // tick. Anything that reads only on the open event sees nothing at all.
        getServer().getScheduler().runTaskLater(this, () -> fill(player, page), 1L);
    }

    private void fill(Player player, int page) {
        Inventory top = player.getOpenInventory().getTopInventory();

        if (top.getSize() != PAGE_SIZE) {
            return;
        }

        List<ItemStack> listings = pages.get(page % PAGES);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            top.setItem(slot, listings.get(slot).clone());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        int page = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        open(player, page);
        player.sendMessage("opened stub auction page " + page);
        return true;
    }

    /** Art unique to each listing, so a wrong map is as visible as a blank one. */
    private static final class Blocks extends MapRenderer {
        private final int seed;

        private Blocks(int seed) {
            this.seed = seed;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    boolean on = ((x / 8) + (y / 8) + this.seed) % 3 == 0;
                    canvas.setPixelColor(x, y, on ? Color.RED : Color.BLUE);
                }
            }
        }
    }
}
