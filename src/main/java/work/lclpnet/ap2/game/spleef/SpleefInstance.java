package work.lclpnet.ap2.game.spleef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.Random;
import java.util.Set;

public class SpleefInstance extends DefaultGameInstance {

    public SpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void onReady() {
        MinecraftServer server = gameHandle.getServer();

        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1, 1);
                player.sendMessage(Text.literal("Spleef has started"));
            }
        }, Ticks.seconds(8));

        scheduler.timeout(() -> {
            var players = PlayerLookup.all(server).toArray(ServerPlayerEntity[]::new);
            ServerPlayerEntity winner = players[new Random().nextInt(players.length)];

            gameHandle.complete(Set.of(winner));
        }, Ticks.seconds(15));
    }
}
