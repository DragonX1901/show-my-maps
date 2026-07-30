package stub;

import java.awt.Color;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stands in for the auction plugins on a real network: a GUI holding a filled map
 * the player has never carried and never will. Exactly the case a vanilla server
 * never sends colours for.
 */
public final class StubAuction extends JavaPlugin implements Listener {
    private ItemStack listing;

    @Override
    public void onEnable() {
        MapView view = Bukkit.createMap(getServer().getWorlds().get(0));
        view.getRenderers().clear();
        view.addRenderer(new Stripes());
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);

        listing = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) listing.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("Auction listing");
        listing.setItemMeta(meta);

        getServer().getPluginManager().registerEvents(this, this);
        // Keeps the GUI up whatever the world does to the player, so a run cannot
        // end up measuring a closed screen.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getSize() != 27) {
                    open(player);
                }
            }
        }, 100L, 60L);
        getLogger().info("Stub auction map id " + view.getId());
    }

    /** Opens itself, so a client can be driven with no input at all. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                open(player);
            }
        }, 100L);
    }

    private void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Auction");
        gui.setItem(13, listing.clone());
        player.openInventory(gui);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        open(player);
        player.sendMessage("opened stub auction");
        return true;
    }

    /** Deterministic art, so the test can tell a real fetch from a blank map. */
    private static final class Stripes extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixelColor(x, y, (x / 8 + y / 8) % 2 == 0 ? Color.RED : Color.BLUE);
                }
            }
        }
    }
}
