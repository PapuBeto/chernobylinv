package com.chernobyl.inv;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChernobylInv.MODID)
public final class Stage4Events {
    private Stage4Events() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Stage4Catalog.load(event.getServer());
        RecipeDatabase.load(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Stage4Catalog.syncTo(player);
            RecipeDatabase.syncTo(player);
            CraftSyncState.send(player);
            CraftingManager.flushFinished(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chernobylinv")
                .then(Commands.literal("reloadcraft")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            Stage4Catalog.load(server);
                            Stage4Catalog.syncAll(server);
                            RecipeDatabase.load(server);
                            RecipeDatabase.syncAll(server);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Catálogos recargados ("
                                            + Stage4Catalog.serverRecipes().size() + " de etapa 4, "
                                            + RecipeDatabase.recipes().size() + " de crafteo)."),
                                    true);
                            return 1;
                        })));
    }
}
