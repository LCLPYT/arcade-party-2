package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
    private final ServerWorld world;
    private final PaintballTeams teams;
    private final PaintManager paintManager;
    private final Set<Tripwire> tripwires = new HashSet<>();

    public TripWireItem(Translations translations, Participants participants, ServerWorld world, PaintballTeams teams,
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
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.TRIPWIRE_HOOK);
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        double range = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);

        HitResult hit = raycast(player, range, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent(), entity -> !entity.isSpectator());

        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return ActionResult.PASS;
        }

        Vec3d pos = blockHit.getPos();
        Vec3d dir = blockHit.getSide().getDoubleVector();

        BlockHitResult opposingHit = raycastBlocks(world, pos.add(dir.multiply(TRIPWIRE_MARGIN)), dir, MAX_LENGTH,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, ShapeContext.absent());

        if (opposingHit.getType() != HitResult.Type.BLOCK) {
            translations.translateText("game.ap2.paintball.item.tripwire.too_long")
                    .formatted(Formatting.RED)
                    .sendTo(player);

            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.2f, 1f);

            return ActionResult.FAIL;
        }

        stack.decrementUnlessCreative(1, player);

        double length = opposingHit.getPos().subtract(pos).length();

        tripwires.add(new Tripwire(pos, dir, length, player.getUuid()));

        float activateVolume = 0.45f, activatePitch = 1.78f;
        float placeVolume = 0.5f, placePitch = 1.3f;

        teams.getTeamManager().getTeam(player).ifPresentOrElse(
                team -> {
                    playSoundFor(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, pos, activateVolume, activatePitch, team.getPlayers());
                    playSoundFor(SoundEvents.BLOCK_IRON_PLACE, SoundCategory.PLAYERS, pos, placeVolume, placePitch, team.getPlayers());
                },
                () -> {
                    playSound(player, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, pos, activateVolume, activatePitch);
                    playSound(player, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, pos, placeVolume, placePitch);
                }
        );

        return ActionResult.SUCCESS_SERVER;
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

        private final Vec3d pos;
        private final Vec3d dir;
        private final double length;
        private final UUID ownerUuid;
        private int timer = 0;

        private Tripwire(Vec3d pos, Vec3d dir, double length, UUID ownerUuid) {
            this.pos = pos;
            this.dir = dir;
            this.length = length;
            this.ownerUuid = ownerUuid;
        }

        public boolean tick() {
            ServerPlayerEntity player = participants.getParticipant(ownerUuid).orElse(null);

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
                DustParticleEffect effect = new DustParticleEffect(team.key().color(), DISPLAY_LASER_SIZE);

                spawnParticleFor(effect, pos.x + dir.x * d, pos.y + dir.y * d, pos.z + dir.z * d,
                        1, 0, 0, 0, 0, team.getPlayers());
            }
        }

        private boolean checkExplosion(ServerPlayerEntity owner, PaintballTeam ownerTeam) {
            HitResult hit = raycastEntities(world, pos.add(dir.multiply(TRIPWIRE_MARGIN)), dir, length, entity
                    -> entity instanceof ServerPlayerEntity player
                    && participants.isParticipating(player)
                    && teams.teamOf(player).map(pbt -> pbt.key() != ownerTeam.key()).orElse(false));

            if (hit.getType() != HitResult.Type.ENTITY || !(hit instanceof EntityHitResult entityHit)) return false;

            paintManager.createExplosion(owner, entityHit.getEntity().getPos(), ownerTeam, EXPLOSION_POWER);

            return true;
        }
    }
}
