package com.james090500.VelocityGUI.helpers;

import com.james090500.VelocityGUI.VelocityGUI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.SoundCategory;
import dev.simplix.protocolize.api.inventory.InventoryClick;
import dev.simplix.protocolize.api.player.ProtocolizePlayer;
import dev.simplix.protocolize.data.Sound;

import java.util.Optional;

public class InventoryClickHandler {

    private VelocityGUI velocityGUI;

    /**
     * Constructor
     * @param velocityGUI
     */
    public InventoryClickHandler(VelocityGUI velocityGUI) {
        this.velocityGUI = velocityGUI;
    }

    /**
     * Handle the gui commands
     * @param commands
     * @param click
     */
    public void execute(String[] commands, InventoryClick click) {
        Player player = velocityGUI.getServer().getPlayer(click.player().uniqueId()).get();
        for(String command : commands) {
            String[] splitCommand = command.split("= ");
            switch(splitCommand[0]) {
                case "open":
                    new InventoryLauncher(velocityGUI).execute(splitCommand[1], player);
                    break;
                case "close":
                    click.player().closeInventory();
                    break;
                case "sudo":
                    player.spoofChatInput(splitCommand[1]);
                    break;
                case "vsudo":
                    velocityGUI.getServer().getCommandManager().executeAsync(player, splitCommand[1]);
                    break;
                case "server":
                    Optional<RegisteredServer> optionalServer = velocityGUI.getServer().getServer(splitCommand[1]);
                    if (optionalServer.isPresent()) {
                        RegisteredServer server = optionalServer.get();
                        player.createConnectionRequest(server).connect().thenAccept(result -> {
                            if (!result.isSuccessful()) {
                                result.getReasonComponent().ifPresent(player::sendMessage);
                                ProtocolizePlayer protocolizePlayer = Protocolize.playerProvider().player(player.getUniqueId());
                                protocolizePlayer.playSound(Sound.ITEM_AXE_SCRAPE, SoundCategory.MASTER, 1f, 1f);
                                click.player().closeInventory();
                            }
                        });
                    } else {
                        ProtocolizePlayer protocolizePlayer = Protocolize.playerProvider().player(player.getUniqueId());
                        protocolizePlayer.playSound(Sound.ITEM_AXE_SCRAPE, SoundCategory.MASTER, 1f, 1f);
                    }
                    break;
            }
        }
    }

}
