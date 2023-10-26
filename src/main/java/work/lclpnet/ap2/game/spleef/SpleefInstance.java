package work.lclpnet.ap2.game.spleef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

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
            gameHandle.complete(Set.of());  // TODO
        }, Ticks.seconds(15));

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                World world = entity.getWorld();
                BlockState state = world.getBlockState(pos);

                return state.isOf(Blocks.SNOW_BLOCK);
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> damageSource.isOf(DamageTypes.LAVA));
        });
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        var participants = gameHandle.getParticipants().getParticipants();
        if (participants.size() > 1) return;

        participants.stream()
                .findAny()
                .ifPresentOrElse(gameHandle::complete, gameHandle::completeWithoutWinner);
    }
}
