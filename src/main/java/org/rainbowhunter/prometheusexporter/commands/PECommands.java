package org.rainbowhunter.prometheusexporter.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public class PECommands {

    private final JavaPlugin plugin;
    private final Runnable reloadAction;

    public PECommands(JavaPlugin plugin, Runnable reloadAction) {
        this.plugin = plugin;
        this.reloadAction = reloadAction;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("pe")
                            .then(Commands.literal("reload")
                                    .requires(src -> src.getSender().hasPermission("prometheusexporter.reload"))
                                    .executes(ctx -> {
                                        reloadAction.run();
                                        ctx.getSource().getSender().sendRichMessage(
                                                "<green>Prometheus Exporter config reloaded "
                                                        + "<gray>(jvm_metrics changes require full restart)");
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "Prometheus Exporter management"
            );
        });
    }
}
