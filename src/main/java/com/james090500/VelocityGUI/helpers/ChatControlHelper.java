package com.james090500.VelocityGUI.helpers;

import com.velocitypowered.api.proxy.Player;

public class ChatControlHelper {

    public static String getNick(Player player) {
        if(!doesClassExist("org.mineacademy.velocitycontrol.SyncedCache")) {
            return null;
        }

        return "";
    }

    /**
     * Checks if a class exists or not
     * @param name
     * @return
     */
    private static boolean doesClassExist(String name) {
        try {
            Class c = Class.forName(name);
            if (c != null) {
                return true;
            }
        } catch (ClassNotFoundException e) {}
        return false;
    }

}
