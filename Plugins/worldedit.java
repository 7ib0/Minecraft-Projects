package me.tibo.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Worldedit extends JavaPlugin implements CommandExecutor, TabExecutor, Listener {
    private static int MAX_REGION_SIZE = 1000000;
    private static int MAX_BLOCK_CHANGES = 100000;
    private static int MAX_UNDO = 20;
    private static final Map<UUID, Selection> selections = new HashMap<>();
    private static final Map<UUID, Deque<EditAction>> undos = new HashMap<>();
    private static final Map<UUID, Deque<EditAction>> redos = new HashMap<>();
    private static final Set<UUID> wands = new HashSet<>();
    private static final Map<UUID, Clipboard> clipboards = new HashMap<>();
    private static final Map<UUID, Brush> brushes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        MAX_REGION_SIZE = config.getInt("max-region-size", 1000000);
        MAX_BLOCK_CHANGES = config.getInt("max-block-changes", 100000);
        MAX_UNDO = config.getInt("max-undo", 20);
        String[] commands = {
                "wand", "pos1", "pos2", "hpos1", "hpos2", "expand", "contract", "shift",
                "set", "replace", "stack", "move", "undo", "redo", "copy", "paste", "brush"
        };
        for (String cmd : commands) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use WorldEdit commands.");
            return true;
        }
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();
        try {
            switch (cmd) {
                case "wand":
                    giveWand(player);
                    player.sendMessage("You have been given the selection wand.");
                    return true;
                case "pos1":
                    setPos1(player, player.getLocation());
                    player.sendMessage("Position 1 set to your current location.");
                    return true;
                case "pos2":
                    setPos2(player, player.getLocation());
                    player.sendMessage("Position 2 set to your current location.");
                    return true;
                case "hpos1":
                    setPos1(player, getBlockLookingAt(player));
                    player.sendMessage("Position 1 set to the block you are looking at.");
                    return true;
                case "hpos2":
                    setPos2(player, getBlockLookingAt(player));
                    player.sendMessage("Position 2 set to the block you are looking at.");
                    return true;
                case "expand":
                    if (args.length < 2) {
                        player.sendMessage("Usage: /expand <amount> <direction>");
                        return true;
                    }
                    int expandAmount = Integer.parseInt(args[0]);
                    Direction expandDir = args.length >= 2 ? parseDirection(args[1]) : Direction.NORTH;
                    expand(player, expandAmount, expandDir);
                    player.sendMessage("Selection expanded.");
                    return true;
                case "contract":
                    if (args.length < 2) {
                        player.sendMessage("Usage: /contract <amount> <direction>");
                        return true;
                    }
                    int contractAmount = Integer.parseInt(args[0]);
                    Direction contractDir = args.length >= 2 ? parseDirection(args[1]) : Direction.NORTH;
                    contract(player, contractAmount, contractDir);
                    player.sendMessage("Selection contracted.");
                    return true;
                case "shift":
                    if (args.length < 2) {
                        player.sendMessage("Usage: /shift <amount> <direction>");
                        return true;
                    }
                    int shiftAmount = Integer.parseInt(args[0]);
                    Direction shiftDir = args.length >= 2 ? parseDirection(args[1]) : Direction.NORTH;
                    shift(player, shiftAmount, shiftDir);
                    player.sendMessage("Selection shifted.");
                    return true;
                case "set":
                    if (args.length < 1) {
                        player.sendMessage("Usage: /set <block>");
                        return true;
                    }
                    set(player, args[0]);
                    player.sendMessage("Blocks set.");
                    return true;
                case "replace":
                    if (args.length < 2) {
                        player.sendMessage("Usage: /replace <from> <to>");
                        return true;
                    }
                    replace(player, args[0], args[1]);
                    player.sendMessage("Blocks replaced.");
                    return true;
                case "stack":
                    if (args.length < 1) {
                        player.sendMessage("Usage: /stack <count> [direction]");
                        return true;
                    }
                    int stackCount = Integer.parseInt(args[0]);
                    Direction stackDir = args.length >= 2 ? parseDirection(args[1]) : Direction.NORTH;
                    stack(player, stackCount, stackDir);
                    player.sendMessage("Selection stacked.");
                    return true;
                case "move":
                    if (args.length < 1) {
                        player.sendMessage("Usage: /move <count> [direction]");
                        return true;
                    }
                    int moveCount = Integer.parseInt(args[0]);
                    Direction moveDir = args.length >= 2 ? parseDirection(args[1]) : Direction.NORTH;
                    move(player, moveCount, moveDir);
                    player.sendMessage("Selection moved.");
                    return true;
                case "undo":
                    undo(player);
                    player.sendMessage("Undo performed.");
                    return true;
                case "redo":
                    redo(player);
                    player.sendMessage("Redo performed.");
                    return true;
                case "copy":
                    copy(player);
                    return true;
                case "paste":
                    paste(player);
                    return true;
                case "brush":
                    handleBrushCommand(player, args);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            player.sendMessage("Error: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    private void handleBrushCommand(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("Available brushes:");
            player.sendMessage("- /brush sphere <block> <radius> [hollow]");
            player.sendMessage("- /brush cylinder <block> <radius> <height> [hollow]");
            player.sendMessage("- /brush smooth <radius> [iterations]");
            player.sendMessage("- /brush clear");
            player.sendMessage("- /brush info");
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sphere":
                if (args.length < 3) {
                    player.sendMessage("Usage: /brush sphere <block> <radius> [hollow]");
                    return;
                }
                Material sphereMat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                if (sphereMat == null || !sphereMat.isBlock()) {
                    player.sendMessage("Invalid block type: " + args[1]);
                    return;
                }
                int sphereRadius;
                try {
                    sphereRadius = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid radius: " + args[2]);
                    return;
                }
                boolean sphereHollow = args.length >= 4 && Boolean.parseBoolean(args[3]);
                brushes.put(player.getUniqueId(), new SphereBrush(sphereMat, sphereRadius, sphereHollow));
                player.sendMessage("Sphere brush set: " + sphereMat.name().toLowerCase() + ", radius " + sphereRadius + (sphereHollow ? " (hollow)" : ""));
                break;
            case "cylinder":
                if (args.length < 4) {
                    player.sendMessage("Usage: /brush cylinder <block> <radius> <height> [hollow]");
                    return;
                }
                Material cylMat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                if (cylMat == null || !cylMat.isBlock()) {
                    player.sendMessage("Invalid block type: " + args[1]);
                    return;
                }
                int cylRadius, cylHeight;
                try {
                    cylRadius = Integer.parseInt(args[2]);
                    cylHeight = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid radius or height: " + args[2] + ", " + args[3]);
                    return;
                }
                boolean cylHollow = args.length >= 5 && Boolean.parseBoolean(args[4]);
                brushes.put(player.getUniqueId(), new CylinderBrush(cylMat, cylRadius, cylHeight, cylHollow));
                player.sendMessage("Cylinder brush set: " + cylMat.name().toLowerCase() + ", radius " + cylRadius + ", height " + cylHeight + (cylHollow ? " (hollow)" : ""));
                break;
            case "smooth":
                if (args.length < 2) {
                    player.sendMessage("Usage: /brush smooth <radius> [iterations]");
                    return;
                }
                int smoothRadius, smoothIterations = 1;
                try {
                    smoothRadius = Integer.parseInt(args[1]);
                    if (args.length >= 3) smoothIterations = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid radius or iterations: " + args[1] + (args.length >= 3 ? ", " + args[2] : ""));
                    return;
                }
                brushes.put(player.getUniqueId(), new SmoothBrush(smoothRadius, smoothIterations));
                player.sendMessage("Smooth brush set: radius " + smoothRadius + ", iterations " + smoothIterations);
                break;
            case "clear":
                brushes.remove(player.getUniqueId());
                player.sendMessage("Brush cleared.");
                break;
            case "info":
                Brush brush = brushes.get(player.getUniqueId());
                if (brush == null) {
                    player.sendMessage("No brush set.");
                } else {
                    player.sendMessage(brush.getInfo());
                }
                break;
            default:
                player.sendMessage("Unknown brush type. Use /brush for help.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("brush")) {
            if (args.length == 1) {
                return Arrays.asList("sphere", "cylinder", "smooth", "clear", "info");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("sphere") || args[0].equalsIgnoreCase("cylinder"))) {
                List<String> blocks = new ArrayList<>();
                for (Material mat : Material.values()) {
                    if (mat.isBlock()) {
                        blocks.add(mat.name().toLowerCase());
                    }
                }
                return blocks;
            }
        }
        if (cmd.equals("expand") || cmd.equals("contract") || cmd.equals("shift") || cmd.equals("stack") || cmd.equals("move")) {
            if (args.length == 2) {
                return Arrays.asList("up", "down", "north", "south", "east", "west");
            }
        }
        if ((cmd.equals("replace") && args.length <= 2) || (cmd.equals("set") && args.length == 1)) {
            List<String> blocks = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (mat.isBlock()) {
                    blocks.add(mat.name().toLowerCase());
                }
            }
            return blocks;
        }
        return Collections.emptyList();
    }

    public static void giveWand(Player player) {
        wands.add(player.getUniqueId());
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.WOODEN_AXE));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        Brush brush = brushes.get(player.getUniqueId());
        if (brush != null && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            Block clicked = player.getTargetBlockExact(100);
            if (clicked != null) {
                Location loc = clicked.getLocation().add(0, 1, 0);
                List<BlockChange> changes = brush.apply(player, loc);
                if (changes == null || changes.isEmpty()) {
                    player.sendMessage("Nothing to change.");
                    return;
                }
                if (changes.size() > MAX_BLOCK_CHANGES) {
                    player.sendMessage("Too many block changes (" + changes.size() + ").");
                    return;
                }
                applyBlockChanges(changes);
                pushUndo(player, new EditAction(changes));
                clearRedo(player);
                player.sendMessage("Brush applied: " + brush.getShortInfo() + " (" + changes.size() + " blocks)");
                event.setCancelled(true);
                return;
            } else {
                player.sendMessage("§cNo block in sight within 100 blocks.");
            }
        }

        if (!wands.contains(player.getUniqueId())) return;
        if (player.getInventory().getItemInMainHand() == null ||
                player.getInventory().getItemInMainHand().getType() != Material.WOODEN_AXE) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            setPos1(player, clicked.getLocation());
            player.sendMessage("Position 1 set to " + locString(clicked.getLocation()));
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            setPos2(player, clicked.getLocation());
            player.sendMessage("Position 2 set to " + locString(clicked.getLocation()));
            event.setCancelled(true);
        }
    }

    private static String locString(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static void setPos1(Player player, Location loc) {
        getOrCreateSelection(player).setPos1(loc);
    }

    public static void setPos2(Player player, Location loc) {
        getOrCreateSelection(player).setPos2(loc);
    }

    public static void expand(Player player, int amount, Direction dir) {
        Selection sel = getOrCreateSelection(player);
        sel.expand(amount, dir);
    }

    public static void contract(Player player, int amount, Direction dir) {
        Selection sel = getOrCreateSelection(player);
        sel.contract(amount, dir);
    }

    public static void shift(Player player, int amount, Direction dir) {
        Selection sel = getOrCreateSelection(player);
        sel.shift(amount, dir);
    }

    public static void set(Player player, String blockId) {
        Selection sel = getOrCreateSelection(player);
        if (!sel.isComplete()) {
            player.sendMessage("Selection incomplete.");
            return;
        }
        Region region = sel.toRegion();
        if (region.size() > MAX_REGION_SIZE) {
            player.sendMessage("Selection too large.");
            return;
        }
        Material block = null;
        try {
            block = Material.matchMaterial(blockId.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {}
        if (block == null || !block.isBlock()) {
            player.sendMessage("Invalid block type: " + blockId);
            return;
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Location loc : region) {
            Block b = loc.getWorld().getBlockAt(loc);
            Material old = b.getType();
            if (!old.equals(block)) {
                changes.add(new BlockChange(loc, old, block));
            }
        }
        if (changes.size() > MAX_BLOCK_CHANGES) {
            player.sendMessage("Too many block changes.");
            return;
        }
        applyBlockChanges(changes);
        pushUndo(player, new EditAction(changes));
        clearRedo(player);
    }

    public static void replace(Player player, String fromId, String toId) {
        Selection sel = getOrCreateSelection(player);
        if (!sel.isComplete()) {
            player.sendMessage("Selection incomplete.");
            return;
        }
        Region region = sel.toRegion();
        if (region.size() > MAX_REGION_SIZE) {
            player.sendMessage("Selection too large.");
            return;
        }
        Material from = Material.matchMaterial(fromId.toUpperCase(Locale.ROOT));
        Material to = Material.matchMaterial(toId.toUpperCase(Locale.ROOT));
        if (from == null || to == null || !from.isBlock() || !to.isBlock()) {
            player.sendMessage("Invalid block type(s): " + fromId + " or " + toId);
            return;
        }
        List<BlockChange> changes = new ArrayList<>();
        for (Location loc : region) {
            Block b = loc.getWorld().getBlockAt(loc);
            Material old = b.getType();
            if (old.equals(from)) {
                changes.add(new BlockChange(loc, old, to));
            }
        }
        if (changes.size() > MAX_BLOCK_CHANGES) {
            player.sendMessage("Too many block changes.");
            return;
        }
        applyBlockChanges(changes);
        pushUndo(player, new EditAction(changes));
        clearRedo(player);
    }

    public static void stack(Player player, int count, Direction dir) {
        Selection sel = getOrCreateSelection(player);
        if (!sel.isComplete()) {
            player.sendMessage("Selection incomplete.");
            return;
        }
        Region region = sel.toRegion();
        if (region.size() * count > MAX_REGION_SIZE) {
            player.sendMessage("Selection too large.");
            return;
        }
        List<BlockChange> changes = new ArrayList<>();
        int dx = dir.dx * region.getWidth();
        int dy = dir.dy * region.getHeight();
        int dz = dir.dz * region.getLength();
        for (int i = 1; i <= count; i++) {
            for (Location loc : region) {
                Location to = loc.clone().add(dx * i, dy * i, dz * i);
                Block bTo = to.getWorld().getBlockAt(to);
                Block bFrom = loc.getWorld().getBlockAt(loc);
                changes.add(new BlockChange(to, bTo.getType(), bFrom.getType()));
            }
        }
        if (changes.size() > MAX_BLOCK_CHANGES) {
            player.sendMessage("Too many block changes.");
            return;
        }
        applyBlockChanges(changes);
        pushUndo(player, new EditAction(changes));
        clearRedo(player);
    }

    public static void move(Player player, int count, Direction dir) {
        Selection sel = getOrCreateSelection(player);
        if (!sel.isComplete()) {
            player.sendMessage("Selection incomplete.");
            return;
        }
        Region region = sel.toRegion();
        if (region.size() > MAX_REGION_SIZE) {
            player.sendMessage("Selection too large.");
            return;
        }
        int dx = dir.dx * count;
        int dy = dir.dy * count;
        int dz = dir.dz * count;
        List<BlockChange> changes = new ArrayList<>();
        Map<Location, Material> oldBlocks = new HashMap<>();
        for (Location loc : region) {
            Block b = loc.getWorld().getBlockAt(loc);
            oldBlocks.put(loc, b.getType());
        }
        for (Location loc : region) {
            Location to = loc.clone().add(dx, dy, dz);
            Block bTo = to.getWorld().getBlockAt(to);
            Material block = oldBlocks.get(loc);
            changes.add(new BlockChange(to, bTo.getType(), block));
            changes.add(new BlockChange(loc, block, Material.AIR));
        }
        if (changes.size() > MAX_BLOCK_CHANGES) {
            player.sendMessage("Too many block changes.");
            return;
        }
        applyBlockChanges(changes);
        pushUndo(player, new EditAction(changes));
        clearRedo(player);
    }

    public static void copy(Player player) {
        Selection sel = getOrCreateSelection(player);
        if (!sel.isComplete()) {
            player.sendMessage("Selection incomplete.");
            return;
        }
        Region region = sel.toRegion();
        if (region.size() > MAX_REGION_SIZE) {
            player.sendMessage("Selection too large.");
            return;
        }
        Location base = sel.getPos1();
        if (base == null) base = player.getLocation().getBlock().getLocation();
        Clipboard clipboard = new Clipboard(region, base);
        clipboards.put(player.getUniqueId(), clipboard);
        player.sendMessage("Copied " + region.size() + " blocks. Use /paste to paste.");
    }

    public static void paste(Player player) {
        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            player.sendMessage("Nothing to paste. Use /copy first.");
            return;
        }
        Location pasteOrigin = player.getLocation().getBlock().getLocation();
        List<BlockChange> changes = clipboard.pasteAt(pasteOrigin);
        if (changes.size() > MAX_BLOCK_CHANGES) {
            player.sendMessage("Too many block changes.");
            return;
        }
        applyBlockChanges(changes);
        pushUndo(player, new EditAction(changes));
        clearRedo(player);
        player.sendMessage("Pasted " + changes.size() + " blocks at your location.");
    }

    public static void undo(Player player) {
        Deque<EditAction> stack = undos.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            player.sendMessage("Nothing to undo.");
            return;
        }
        EditAction action = stack.pop();
        action.undo();
        redos.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(action);
    }

    public static void redo(Player player) {
        Deque<EditAction> stack = redos.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            player.sendMessage("Nothing to redo.");
            return;
        }
        EditAction action = stack.pop();
        action.redo();
        undos.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(action);
    }

    private static Selection getOrCreateSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), k -> new Selection(player.getWorld()));
    }

    private static void applyBlockChanges(List<BlockChange> changes) {
        for (BlockChange c : changes) {
            Block b = c.loc.getWorld().getBlockAt(c.loc);
            b.setType(c.to);
        }
    }

    private static void pushUndo(Player player, EditAction action) {
        Deque<EditAction> stack = undos.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > MAX_UNDO) stack.removeLast();
    }

    private static void clearRedo(Player player) {
        Deque<EditAction> stack = redos.get(player.getUniqueId());
        if (stack != null) stack.clear();
    }

    private static Location getBlockLookingAt(Player player) {
        Block target = player.getTargetBlockExact(100);
        if (target != null) {
            return target.getLocation();
        }
        return player.getLocation();
    }

    private static Direction parseDirection(String s) {
        switch (s.toLowerCase()) {
            case "up": return Direction.UP;
            case "down": return Direction.DOWN;
            case "north": return Direction.NORTH;
            case "south": return Direction.SOUTH;
            case "east": return Direction.EAST;
            case "west": return Direction.WEST;
            default: throw new IllegalArgumentException("Unknown direction: " + s);
        }
    }
}

interface Brush {
    List<BlockChange> apply(Player player, Location center);
    String getInfo();
    String getShortInfo();
}

class SphereBrush implements Brush {
    private final Material type;
    private final int radius;
    private final boolean hollow;

    public SphereBrush(Material type, int radius, boolean hollow) {
        this.type = type;
        this.radius = radius;
        this.hollow = hollow;
    }

    @Override
    public List<BlockChange> apply(Player player, Location center) {
        List<BlockChange> changes = new ArrayList<>();
        World world = center.getWorld();
        int r2 = radius * radius;
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minY = center.getBlockY() - radius;
        int maxY = center.getBlockY() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int dx = x - center.getBlockX();
                    int dy = y - center.getBlockY();
                    int dz = z - center.getBlockZ();
                    int dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > r2) continue;
                    if (hollow && dist2 < (radius - 1) * (radius - 1)) continue;
                    Location loc = new Location(world, x, y, z);
                    Block b = world.getBlockAt(loc);
                    if (b.getType() != type) {
                        changes.add(new BlockChange(loc, b.getType(), type));
                    }
                }
            }
        }
        return changes;
    }

    @Override
    public String getInfo() {
        return "Current Brush: Sphere\nBlock: " + type.name().toLowerCase() + "\nRadius: " + radius + "\nHollow: " + hollow;
    }

    @Override
    public String getShortInfo() {
        return "Sphere (" + type.name().toLowerCase() + ", r=" + radius + (hollow ? ", hollow" : "") + ")";
    }
}

class CylinderBrush implements Brush {
    private final Material type;
    private final int radius;
    private final int height;
    private final boolean hollow;

    public CylinderBrush(Material type, int radius, int height, boolean hollow) {
        this.type = type;
        this.radius = radius;
        this.height = height;
        this.hollow = hollow;
    }

    @Override
    public List<BlockChange> apply(Player player, Location center) {
        List<BlockChange> changes = new ArrayList<>();
        World world = center.getWorld();
        int r2 = radius * radius;
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minY = center.getBlockY();
        int maxY = center.getBlockY() + height - 1;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int dx = x - center.getBlockX();
                    int dz = z - center.getBlockZ();
                    int dist2 = dx * dx + dz * dz;
                    if (dist2 > r2) continue;
                    if (hollow && dist2 < (radius - 1) * (radius - 1)) continue;
                    Location loc = new Location(world, x, y, z);
                    Block b = world.getBlockAt(loc);
                    if (b.getType() != type) {
                        changes.add(new BlockChange(loc, b.getType(), type));
                    }
                }
            }
        }
        return changes;
    }

    @Override
    public String getInfo() {
        return "Current Brush: Cylinder\nBlock: " + type.name().toLowerCase() + "\nRadius: " + radius + "\nHeight: " + height + "\nHollow: " + hollow;
    }

    @Override
    public String getShortInfo() {
        return "Cylinder (" + type.name().toLowerCase() + ", r=" + radius + ", h=" + height + (hollow ? ", hollow" : "") + ")";
    }
}

class SmoothBrush implements Brush {
    private final int radius;
    private final int iterations;

    public SmoothBrush(int radius, int iterations) {
        this.radius = radius;
        this.iterations = iterations;
    }

    @Override
    public List<BlockChange> apply(Player player, Location center) {
        World world = center.getWorld();
        Set<Material> terrain = new HashSet<>(Arrays.asList(
                Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.SAND, Material.GRAVEL, Material.CLAY, Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM, Material.SNOW_BLOCK, Material.SANDSTONE
        ));
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        int minY = 1;
        int maxY = world.getMaxHeight() - 1;

        Map<String, Integer> heights = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    Block b = world.getBlockAt(x, y, z);
                    if (terrain.contains(b.getType())) {
                        heights.put(x + "," + z, y);
                        break;
                    }
                }
            }
        }

        Map<String, Integer> smoothed = new HashMap<>(heights);
        for (int iter = 0; iter < iterations; iter++) {
            Map<String, Integer> next = new HashMap<>(smoothed);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String key = x + "," + z;
                    if (!smoothed.containsKey(key)) continue;
                    int sum = 0, count = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            String nkey = (x + dx) + "," + (z + dz);
                            if (smoothed.containsKey(nkey)) {
                                sum += smoothed.get(nkey);
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        next.put(key, Math.round((float) sum / count));
                    }
                }
            }
            smoothed = next;
        }

        List<BlockChange> changes = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String key = x + "," + z;
                if (!heights.containsKey(key) || !smoothed.containsKey(key)) continue;
                int oldY = heights.get(key);
                int newY = smoothed.get(key);
                if (oldY == newY) continue;
                for (int y = newY + 1; y <= oldY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (terrain.contains(b.getType())) {
                        changes.add(new BlockChange(b.getLocation(), b.getType(), Material.AIR));
                    }
                }
                for (int y = oldY + 1; y <= newY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    changes.add(new BlockChange(b.getLocation(), b.getType(), Material.DIRT));
                }
                Block top = world.getBlockAt(x, newY, z);
                changes.add(new BlockChange(top.getLocation(), top.getType(), Material.GRASS_BLOCK));
            }
        }
        return changes;
    }

    @Override
    public String getInfo() {
        return "Current Brush: Smooth\nRadius: " + radius + "\nIterations: " + iterations;
    }

    @Override
    public String getShortInfo() {
        return "Smooth (r=" + radius + ", it=" + iterations + ")";
    }
}

class Selection {
    private Location pos1;
    private Location pos2;
    private final World world;
    public Selection(World world) {
        this.world = world;
    }
    public void setPos1(Location l) { pos1 = l.clone(); }
    public void setPos2(Location l) { pos2 = l.clone(); }
    public boolean isComplete() { return pos1 != null && pos2 != null; }
    public Region toRegion() { return new Region(pos1, pos2); }
    public void expand(int amount, Direction dir) {
        if (pos1 == null || pos2 == null) return;
        if (dir == Direction.UP) {
            if (pos1.getBlockY() < pos2.getBlockY()) pos2 = pos2.clone().add(0, amount, 0);
            else pos1 = pos1.clone().add(0, amount, 0);
        } else if (dir == Direction.DOWN) {
            if (pos1.getBlockY() < pos2.getBlockY()) pos1 = pos1.clone().add(0, -amount, 0);
            else pos2 = pos2.clone().add(0, -amount, 0);
        } else if (dir == Direction.NORTH) {
            if (pos1.getBlockZ() < pos2.getBlockZ()) pos1 = pos1.clone().add(0, 0, -amount);
            else pos2 = pos2.clone().add(0, 0, -amount);
        } else if (dir == Direction.SOUTH) {
            if (pos1.getBlockZ() < pos2.getBlockZ()) pos2 = pos2.clone().add(0, 0, amount);
            else pos1 = pos1.clone().add(0, 0, amount);
        } else if (dir == Direction.EAST) {
            if (pos1.getBlockX() < pos2.getBlockX()) pos2 = pos2.clone().add(amount, 0, 0);
            else pos1 = pos1.clone().add(amount, 0, 0);
        } else if (dir == Direction.WEST) {
            if (pos1.getBlockX() < pos2.getBlockX()) pos1 = pos1.clone().add(-amount, 0, 0);
            else pos2 = pos2.clone().add(-amount, 0, 0);
        }
    }
    public void contract(int amount, Direction dir) {
        expand(-amount, dir);
    }
    public void shift(int amount, Direction dir) {
        if (pos1 == null || pos2 == null) return;
        int dx = dir.dx * amount;
        int dy = dir.dy * amount;
        int dz = dir.dz * amount;
        pos1 = pos1.clone().add(dx, dy, dz);
        pos2 = pos2.clone().add(dx, dy, dz);
    }
    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }
}

class Region implements Iterable<Location> {
    private final Location min;
    private final Location max;
    public Region(Location a, Location b) {
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        min = new Location(a.getWorld(), minX, minY, minZ);
        max = new Location(a.getWorld(), maxX, maxY, maxZ);
    }
    public int size() {
        return (max.getBlockX() - min.getBlockX() + 1) * (max.getBlockY() - min.getBlockY() + 1) * (max.getBlockZ() - min.getBlockZ() + 1);
    }
    public int getWidth() { return max.getBlockX() - min.getBlockX() + 1; }
    public int getHeight() { return max.getBlockY() - min.getBlockY() + 1; }
    public int getLength() { return max.getBlockZ() - min.getBlockZ() + 1; }
    public Location getMin() { return min; }
    public Location getMax() { return max; }
    public Iterator<Location> iterator() {
        return new Iterator<Location>() {
            int x = min.getBlockX(), y = min.getBlockY(), z = min.getBlockZ();
            boolean done = false;
            @Override
            public boolean hasNext() { return !done; }
            @Override
            public Location next() {
                Location l = new Location(min.getWorld(), x, y, z);
                if (++x > max.getBlockX()) {
                    x = min.getBlockX();
                    if (++y > max.getBlockY()) {
                        y = min.getBlockY();
                        if (++z > max.getBlockZ()) {
                            done = true;
                        }
                    }
                }
                return l;
            }
        };
    }
}

class EditAction {
    private final List<BlockChange> changes;
    public EditAction(List<BlockChange> changes) {
        this.changes = new ArrayList<>(changes);
    }
    public void undo() {
        for (BlockChange c : changes) {
            Block b = c.loc.getWorld().getBlockAt(c.loc);
            b.setType(c.from);
        }
    }
    public void redo() {
        for (BlockChange c : changes) {
            Block b = c.loc.getWorld().getBlockAt(c.loc);
            b.setType(c.to);
        }
    }
}

class BlockChange {
    public final Location loc;
    public final Material from;
    public final Material to;
    public BlockChange(Location loc, Material from, Material to) {
        this.loc = loc.clone();
        this.from = from;
        this.to = to;
    }
}

enum Direction {
    UP(0,1,0), DOWN(0,-1,0), NORTH(0,0,-1), SOUTH(0,0,1), EAST(1,0,0), WEST(-1,0,0);
    public final int dx, dy, dz;
    Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
}

class Clipboard {
    private final List<ClipboardBlock> blocks = new ArrayList<>();
    private final int minX, minY, minZ;
    private final int width, height, length;
    private final World world;
    public Clipboard(Region region, Location base) {
        this.world = base.getWorld();
        Location min = region.getMin();
        Location max = region.getMax();
        this.minX = min.getBlockX();
        this.minY = min.getBlockY();
        this.minZ = min.getBlockZ();
        this.width = max.getBlockX() - minX + 1;
        this.height = max.getBlockY() - minY + 1;
        this.length = max.getBlockZ() - minZ + 1;
        for (Location loc : region) {
            Block b = loc.getWorld().getBlockAt(loc);
            int relX = loc.getBlockX() - base.getBlockX();
            int relY = loc.getBlockY() - base.getBlockY();
            int relZ = loc.getBlockZ() - base.getBlockZ();
            blocks.add(new ClipboardBlock(relX, relY, relZ, b.getType()));
        }
    }
    public List<BlockChange> pasteAt(Location origin) {
        List<BlockChange> changes = new ArrayList<>();
        World w = origin.getWorld();
        for (ClipboardBlock cb : blocks) {
            Location to = origin.clone().add(cb.relX, cb.relY, cb.relZ);
            Block b = w.getBlockAt(to);
            changes.add(new BlockChange(to, b.getType(), cb.type));
        }
        return changes;
    }
}

class ClipboardBlock {
    public final int relX, relY, relZ;
    public final Material type;
    public ClipboardBlock(int relX, int relY, int relZ, Material type) {
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.type = type;
    }
}
