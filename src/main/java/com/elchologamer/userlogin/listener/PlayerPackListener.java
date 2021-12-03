package com.elchologamer.userlogin.listener;

import com.elchologamer.userlogin.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class PlayerPackListener implements Listener {

    @EventHandler
    private void packStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)
            ULPlayer.get(event.getPlayer()).onJoin(false);
    }
}
