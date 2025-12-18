package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.game.paintball.util.PaintManager;
import work.lclpnet.ap2.game.paintball.util.PaintballTeam;
import work.lclpnet.ap2.game.paintball.util.PaintballTeams;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.ParticleHelper.spawnParticleFor;
import static work.lclpnet.ap2.impl.util.RayCastUtil.*;
import static work.lclpnet.ap2.impl.util.SoundHelper.playSound;
import static work.lclpnet.ap2.impl.util.SoundHelper.playSoundFor;

public class TripWireItem implements SpecialItem {

    private static final double
            MAX_LENGTH = 7,
            TRIPWIRE_MARGIN = 0.01;

    private final Translations translations;
    private final Participants participants;
    private final ServerLevel world;
    private final PaintballTeams teams;
    private final PaintManager paintManager;
    private final Set<Tripwire> tripwires = new HashSet<>();

    public TripWireItem(Translations translations, Participants participants, ServerLevel world, PaintballTeams teams,
                        PaintManager paintManager) {
        this.translations = translations;
        this.participants = participants;
        this.world = world;
        this.teams = teams;
        this.paintManager = paintManager;
    }

    @Override
    public String id() {
        return "tripwire";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.TRIPWIRE_HOOK);
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

        HitResult hit = raycast(player, range, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty(), entity -> !entity.isSpectator());

        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return InteractionResult.PASS;
        }

        Vec3 pos = blockHit.getLocation();
        Vec3 dir = blockHit.getDirection().getUnitVec3();

        BlockHitResult opposingHit = raycastBlocks(world, pos.add(dir.scale(TRIPWIRE_MARGIN)), dir, MAX_LENGTH,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());

        if (opposingHit.getType() != HitResult.Type.BLOCK) {
            translations.translateText("game.ap2.paintball.item.tripwire.too_long")
                    .formatted(ChatFormatting.RED)
                    .sendTo(player);

            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.2f, 1f);

            return InteractionResult.FAIL;
        }

        stack.consume(1, player);

        double length = opposingHit.getLocation().subtract(pos).length();

        tripwires.add(new Tripwire(pos, dir, length, player.getUUID()));

        float activateVolume = 0.45f, activatePitch = 1.78f;
        float placeVolume = 0.5f, placePitch = 1.3f;

        teams.getTeamManager().getTeam(player).ifPresentOrElse(
                team -> {
                    playSoundFor(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, activateVolume, activatePitch, team.getPlayers());
                    playSoundFor(SoundEvents.IRON_PLACE, SoundSource.PLAYERS, pos, placeVolume, placePitch, team.getPlayers());
                },
                () -> {
                    playSound(player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, activateVolume, activatePitch);
                    playSound(player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, placeVolume, placePitch);
                }
        );

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void scheduleTasks(TaskScheduler scheduler, SpecialItemContext ctx) {
        scheduler.interval(this::tick, 1);
    }

    private void tick() {
        tripwires.removeIf(Tripwire::tick);
    }

    private class Tripwire {

        private static final float
                DISPLAY_LASER_SPACING = 0.125f,
                DISPLAY_LASER_SIZE = 0.25f,
                DISPLAY_LASER_TICKS = 4,
                EXPLOSION_POWER = 3.5f;

        private final Vec3 pos;
        private final Vec3 dir;
        private final double length;
        private final UUID ownerUuid;
        private int timer = 0;

        private Tripwire(Vec3 pos, Vec3 dir, double length, UUID ownerUuid) {
            this.pos = pos;
            this.dir = dir;
            this.length = length;
            this.ownerUuid = ownerUuid;
        }

        public boolean tick() {
            ServerPlayer player = participants.getParticipant(ownerUuid).orElse(null);

            if (player == null) return true;

            Team team = teams.getTeamManager().getTeam(player).orElse(null);

            if (team == null) return true;

            PaintballTeam paintballTeam = teams.teamOf(player).orElse(null);

            if (paintballTeam == null) return true;

            if (timer++ % DISPLAY_LASER_TICKS == 0) {
                showTo(team);
            }

            return checkExplosion(player, paintballTeam);
        }

        private void showTo(Team team) {
            for (double d = 0; d <= length; d += DISPLAY_LASER_SPACING) {
                DustParticleOptions effect = new DustParticleOptions(team.key().color(), DISPLAY_LASER_SIZE);

                spawnParticleFor(effect, pos.x + dir.x * d, pos.y + dir.y * d, pos.z + dir.z * d,
                        1, 0, 0, 0, 0, team.getPlayers());
            }
        }

        private boolean checkExplosion(ServerPlayer owner, PaintballTeam ownerTeam) {
            HitResult hit = raycastEntities(world, pos.add(dir.scale(TRIPWIRE_MARGIN)), dir, length, entity
                    -> entity instanceof ServerPlayer player
                    && participants.isParticipating(player)
                    && teams.teamOf(player).map(pbt -> pbt.key() != ownerTeam.key()).orElse(false));

            if (hit.getType() != HitResult.Type.ENTITY || !(hit instanceof EntityHitResult entityHit)) return false;

            paintManager.createExplosion(owner, entityHit.getEntity().position(), ownerTeam, EXPLOSION_POWER);

            return true;
        }
    }
}
