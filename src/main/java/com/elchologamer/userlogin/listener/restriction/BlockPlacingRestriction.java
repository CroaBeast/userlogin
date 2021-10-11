package com.elchologamer.userlogin.listener.restriction;

import com.elchologamer.userlogin.api.UserLoginAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlacingRestriction extends BaseRestriction<BlockPlaceEvent> {

    public BlockPlacingRestriction() {
        super("blockPlacing");
    }

    @Override
    public boolean shouldRestrict(BlockPlaceEvent e) {
        return super.shouldRestrict(e) && !UserLoginAPI.isLoggedIn(e.getPlayer());
    }

    @EventHandler
    public void handle(BlockPlaceEvent e) {
        if (shouldRestrict(e)) e.setCancelled(true);
    }
}