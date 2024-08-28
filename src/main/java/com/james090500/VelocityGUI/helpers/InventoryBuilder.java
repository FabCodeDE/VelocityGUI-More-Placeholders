package com.james090500.VelocityGUI.helpers;

import com.james090500.VelocityGUI.VelocityGUI;
import com.james090500.VelocityGUI.config.Configs;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import dev.simplix.protocolize.api.inventory.Inventory;
import dev.simplix.protocolize.api.item.BaseItemStack;
import dev.simplix.protocolize.api.item.ItemStack;
import dev.simplix.protocolize.api.player.ProtocolizePlayer;
import dev.simplix.protocolize.data.ItemType;
import dev.simplix.protocolize.data.inventory.InventoryType;
import lombok.AccessLevel;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.querz.nbt.tag.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter
public class InventoryBuilder {

    @Getter(AccessLevel.NONE) private VelocityGUI velocityGUI;
    private Player player;
    private InventoryType rows;
    private Component title;
    private List<BaseItemStack> emptyItems = new ArrayList<>();
    private HashMap<Integer, ItemStack> items = new HashMap<>();

    /**
     * The builder
     * @param velocityGUI
     * @param player
     */
    public InventoryBuilder(VelocityGUI velocityGUI, Player player) {
        this.velocityGUI = velocityGUI;
        this.player = player;
    }

    /**
     * Sets the rows of the GUI to display
     * @param rows
     */
    public void setRows(int rows) {
        this.rows = getInventoryType(rows);
    }

    /**
     * Sets the title and converts a string to a Component
     * @param title
     */
    public void setTitle(String title) {
        this.title = PlaceholderParser.of(this.player, title);
    }

    /**
     * Set the empty item
     * @param item
     */
    public void setEmpty(String item) {
        ItemStack itemStack = new ItemStack(ItemType.valueOf(item));
        itemStack.displayName("");
        itemStack.amount((byte) 1);

        int totalSlots = this.getRows().getTypicalSize(player.getProtocolVersion().getProtocol());
        for(int i = 0; i < totalSlots; i++) {
            emptyItems.add(itemStack);
        }
    }

    public void updateServerItems(HashMap<Integer, Configs.Item> guiItems, Inventory inventory, ProtocolizePlayer protocolizePlayer) {
        guiItems.forEach((index, guiItem) -> {
            //Set the item Material, Name and Amount
            ItemStack itemStack;
                try {

                    itemStack = new ItemStack(ItemType.valueOf(guiItem.getMaterial()));

                    List<String> serverCommands = Arrays.stream(guiItem.getCommands())
                            .filter(s -> s.contains("server"))
                            .collect(Collectors.toList());

                    if(!serverCommands.isEmpty()) {
                        var serverCommand = serverCommands.get(0);  // Get the first command
                        var serverName = serverCommand.split("= ")[1];
                        Optional<RegisteredServer> optionalServer = velocityGUI.getServer().getServer(serverName);
                        if (optionalServer.isPresent()) {
                            getServerStatusItem(serverName, itemStack, itemStackUpdated -> {
                                // Update your GUI with the itemStack here
                                itemStackUpdated.displayName(PlaceholderParser.of(this.player, guiItem.getName()));
                                itemStackUpdated.amount(guiItem.getStack());
                                if(guiItem.getLore() != null) {
                                    itemStackUpdated.addToLore(PlaceholderParser.of(this.player, (itemStackUpdated.itemType() == ItemType.REDSTONE_BLOCK ? "&cOffline" : "&aOnline")));
                                    if(itemStackUpdated.itemType() != ItemType.REDSTONE_BLOCK) {
                                        for (String lore : guiItem.getLore()) {
                                            itemStackUpdated.addToLore(PlaceholderParser.of(this.player, lore));
                                        }
                                    }

                                }

                                //Get the item NBT
                                CompoundTag tag = itemStackUpdated.nbtData();

                                //Set enchantment on the item if needed
                                if(guiItem.isEnchanted()) {
                                    ListTag<CompoundTag> enchantments = new ListTag<>(CompoundTag.class);
                                    CompoundTag enchantment = new CompoundTag();
                                    enchantment.put("id", new StringTag("minecraft:unbreaking"));
                                    enchantment.put("lvl", new ShortTag((short) 1));
                                    enchantments.add(enchantment);
                                    tag.put("Enchantments", enchantments);
                                }

                                tag.put("HideFlags", new IntTag(99));
                                tag.put("overrideMeta", new ByteTag((byte)1));

                                inventory.item(index, itemStackUpdated);
                                protocolizePlayer.openInventory(inventory);
                            });


                            // Update your GUI here with the itemStack
                        }
                    }
                } catch (IllegalArgumentException e) {
                    this.velocityGUI.getLogger().error("Invalid Material! " + guiItem.getMaterial());
                }

        });
    }

    private static class CachedPingResult {
        private final boolean online;
        private final long timestamp;

        public CachedPingResult(boolean online, long timestamp) {
            this.online = online;
            this.timestamp = timestamp;
        }

        public boolean isOnline() {
            return online;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
    private static final long CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(10); // Cache duration (e.g., 10 seconds)
    private final HashMap<String, CachedPingResult> pingCache = new HashMap<>();


    public void getServerStatusItem(String serverName, ItemStack defaultItem, Consumer<ItemStack> callback) {
        Optional<RegisteredServer> optionalServer = velocityGUI.getServer().getServer(serverName);

        if (optionalServer.isPresent()) {
            RegisteredServer server = optionalServer.get();

            CachedPingResult cachedPing = pingCache.get(serverName);

            // Check if cache is valid
            if (cachedPing != null && (System.currentTimeMillis() - cachedPing.getTimestamp()) < CACHE_EXPIRY) {
                callback.accept(cachedPing.isOnline() ? defaultItem : new ItemStack(ItemType.REDSTONE_BLOCK));
                return;
            }

            // Asynchronous ping operation
            server.ping(PingOptions.builder()
                            .version(ProtocolVersion.MINECRAFT_1_18_2).timeout(100, TimeUnit.MILLISECONDS)
                            .build())
                    .whenComplete((serverPing, throwable) -> {
                        if (throwable == null) {
                            // Server is online, cache result
                            pingCache.put(serverName, new CachedPingResult(true, System.currentTimeMillis()));
                            callback.accept(defaultItem); // Use the default item for online status
                        } else {
                            // Server is offline or an error occurred, cache result
                            pingCache.put(serverName, new CachedPingResult(false, System.currentTimeMillis()));
                            callback.accept(new ItemStack(ItemType.REDSTONE_BLOCK)); // Use redstone block for offline status
                        }
                    });
        } else {
            callback.accept(new ItemStack(ItemType.REDSTONE_BLOCK)); // Default to redstone block if the server is not found
        }
    }
    /**
     * Add items to the panel
     * @param guiItems
     */
    public void setItems(HashMap<Integer, Configs.Item> guiItems) {
        guiItems.forEach((index, guiItem) -> {
            //Set the item Material, Name and Amount
            ItemStack itemStack;
            if(guiItem.getMaterial().startsWith("head=")) {
                itemStack = new ItemStack(ItemType.PLAYER_HEAD);
            } else {
                try {

                    itemStack = new ItemStack(ItemType.valueOf(guiItem.getMaterial()));

                    List<String> serverCommands = Arrays.stream(guiItem.getCommands())
                            .filter(s -> s.contains("server"))
                            .collect(Collectors.toList());

                    if(!serverCommands.isEmpty()) {
                        var serverCommand = serverCommands.get(0);  // Get the first command
                        var serverName = serverCommand.split("= ")[1];
                        Optional<RegisteredServer> optionalServer = velocityGUI.getServer().getServer(serverName);
                        if (optionalServer.isPresent()) {
                            itemStack = new ItemStack(ItemType.CONDUIT);


                            // Update your GUI here with the itemStack
                        }
                    }
                } catch (IllegalArgumentException e) {
                    itemStack = new ItemStack(ItemType.STONE);
                    this.velocityGUI.getLogger().error("Invalid Material! " + guiItem.getMaterial());
                }
            }

            itemStack.displayName(PlaceholderParser.of(this.player, guiItem.getName()));
            itemStack.amount(guiItem.getStack());

            //Set any lore on the item
            if(guiItem.getLore() != null) {
                for (String lore : guiItem.getLore()) {
                    itemStack.addToLore(PlaceholderParser.of(this.player, lore));
                }
            }

            //Get the item NBT
            CompoundTag tag = itemStack.nbtData();

            //Set enchantment on the item if needed
            if(guiItem.isEnchanted()) {
                ListTag<CompoundTag> enchantments = new ListTag<>(CompoundTag.class);
                CompoundTag enchantment = new CompoundTag();
                enchantment.put("id", new StringTag("minecraft:unbreaking"));
                enchantment.put("lvl", new ShortTag((short) 1));
                enchantments.add(enchantment);
                tag.put("Enchantments", enchantments);
            }

            //If a player heads lets do this
            if(guiItem.getMaterial().startsWith("head= ")) {
                String headData = guiItem.getMaterial().replace("head= ", "");
                if(headData.equals("self")) {
                    tag.put("SkullOwner", new StringTag(player.getUsername()));

                } else {
                    CompoundTag skullOwnerTag = tag.getCompoundTag("SkullOwner");
                    CompoundTag propertiesTag = tag.getCompoundTag("Properties");
                    ListTag<CompoundTag> texturesTag = new ListTag<>(CompoundTag.class);
                    CompoundTag textureTag = new CompoundTag();

                    if (skullOwnerTag == null) {
                        skullOwnerTag = new CompoundTag();
                    }
                    if (propertiesTag == null) {
                        propertiesTag = new CompoundTag();
                    }

                    textureTag.put("Value", new StringTag(headData));
                    texturesTag.add(textureTag);
                    propertiesTag.put("textures", texturesTag);
                    skullOwnerTag.put("Properties", propertiesTag);
                    skullOwnerTag.put("Name", new StringTag(headData));
                    tag.put("SkullOwner", skullOwnerTag);
                }

                //Set item NBT
                itemStack.nbtData(tag);
            }

            tag.put("HideFlags", new IntTag(99));
            tag.put("overrideMeta", new ByteTag((byte)1));

            //Add to a hashmap
            items.put(index, itemStack);
        });
    }

    /**
     * Get the type of inventory by the rows
     * @param value
     * @return
     */
    private InventoryType getInventoryType(int value) {
        switch(value) {
            case 1:
                return InventoryType.GENERIC_9X1;
            case 2:
                return InventoryType.GENERIC_9X2;
            case 3:
                return InventoryType.GENERIC_9X3;
            case 4:
                return InventoryType.GENERIC_9X4;
            case 5:
                return InventoryType.GENERIC_9X5;
            default:
                return InventoryType.GENERIC_9X6;
        }
    }

    /**
     * Build the inventory
     * @return
     */
    public Inventory build() {
        Inventory inventory = new Inventory(this.getRows());
        inventory.title(this.getTitle());
        inventory.items(this.getEmptyItems());

        this.getItems().forEach((index, item) -> {
            inventory.item(index, item);
        });

        return inventory;
    }

}
