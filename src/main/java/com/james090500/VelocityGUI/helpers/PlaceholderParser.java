package com.james090500.VelocityGUI.helpers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderParser {


    private static ProxyServer proxy;

    public static Component of(Player player, String rawString) {
        //Username
        if(rawString.contains("%username%")) {
            rawString = rawString.replaceAll("%username%", player.getUsername());
        }

        //Server Name
        if(rawString.contains("%server_name%")) {
            rawString = rawString.replaceAll("%server_name%", player.getCurrentServer().get().getServerInfo().getName());
        }

        //ChatControlRed
        if(rawString.contains("%chatcontrolred_nick%")) {
            String nickname = ChatControlHelper.getNick(player);
            rawString = rawString.replaceAll("%chatcontrolred_nick%", nickname);
        }

        //LuckPerms Meta
        if(rawString.startsWith("%luckperms_meta")) {
            String queryOption = rawString.replaceAll("%", "").replaceAll("luckperms_meta_", "");
            LuckPermsHelper.getMeta(player, queryOption);
        }

        // Handle %online% placeholder
        if (rawString.contains("%online%")) {
            int online = proxy.getAllPlayers().size();
            rawString = rawString.replace("%online%", String.valueOf(online));
        }

        // Dynamic placeholders for server-specific stats
        Pattern pattern = Pattern.compile("%(\\w+)_online%");
        Matcher matcher = pattern.matcher(rawString);
        while (matcher.find()) {
            String serverName = matcher.group(1);
            Optional<RegisteredServer> server = proxy.getServer(serverName);
            int online = server.map(s -> s.getPlayersConnected().size()).orElse(0);
            rawString = rawString.replace("%" + serverName + "_online%", String.valueOf(online));
        }

        pattern = Pattern.compile("%(\\w+)_status%");
        matcher = pattern.matcher(rawString);
        while (matcher.find()) {
            String serverName = matcher.group(1);
            Optional<RegisteredServer> server = proxy.getServer(serverName);
            String status = server.isPresent() ? "online" : "offline";
            rawString = rawString.replace("%" + serverName + "_status%", status);
        }


        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(rawString);
        return component;
    }

    public static void setProxy(ProxyServer proxy) {
        PlaceholderParser.proxy = proxy;
    }

}
