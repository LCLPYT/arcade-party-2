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

public class SpleefInstance extends DefaultGameInstance {

    public SpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void onReady() {
        MinecraftServer server = gameHandle.getServer();

        gameHandle.getScheduler().timeout(() -> {
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1, 1);
                player.sendMessage(Text.literal("Spleef has started"));
            }
        }, Ticks.seconds(8));
    }
}
