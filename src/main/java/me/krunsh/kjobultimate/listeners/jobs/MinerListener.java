package me.krunsh.kjobultimate.listeners.jobs;

import java.util.Objects;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import me.krunsh.kjobultimate.KjobUltimate;

/** Casse Bukkit ordinaire ; Kminerai possède son bridge post-casse dédié. */
public final class MinerListener implements Listener {

    private final KjobUltimate plugin;

    public MinerListener(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null."
        );
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    @SuppressWarnings("deprecation")
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block == null) {
            return;
        }

        plugin.getMiningActionService()
            .apply(
                event.getPlayer(),
                block,
                block.getType(),
                block.getData() & 0xFF,
                null,
                null
            );
    }
}
