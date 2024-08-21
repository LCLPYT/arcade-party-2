package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.game.team.TeamSpawnAccess;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.prot.ProtectionConfig;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

public class CCHooks {

    private final Participants participants;
    private final TeamManager teamManager;
    private final TeamSpawnAccess spawnAccess;
    private final Translations translations;
    private final Args args;

    public CCHooks(Participants participants, TeamManager teamManager, TeamSpawnAccess spawnAccess,
                   Translations translations, Args args) {
        this.participants = participants;
        this.teamManager = teamManager;
        this.spawnAccess = spawnAccess;
        this.translations = translations;
        this.args = args;
    }

    public void configure(ProtectionConfig config) {
        CCFuel fuel = args.fuel();
        CCBaseManager baseManager = args.baseManager();

        config.allow(ProtectionTypes.PICKUP_ITEM, ProtectionTypes.SWAP_HAND_ITEMS, ProtectionTypes.PICKUP_PROJECTILE);

        config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                return participants.isParticipating(player) && !baseManager.isInBase(player);
            }

            return entity instanceof BoatEntity;  // allow damaging boats
        });

        config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return false;

            return fuel.isFuel(player, pos);
        });

        config.allow(ProtectionTypes.BLOCK_ITEM_DROP, (world, blockPos, itemStack) -> fuel.isFuel(itemStack));

        config.allow(ProtectionTypes.DROP_ITEM, (player, slot, inInventory) -> {
            if (inInventory || slot < 0 || slot > 8) return true;

            // drop hot-bar item via the drop key while not in inventory
            ItemStack stack = player.getInventory().getStack(slot);

            return fuel.isFuel(stack);
        });

        config.allow(ProtectionTypes.MODIFY_INVENTORY, clickEvent -> {
            final int slot = clickEvent.slot();

            // disable armor interaction
            if (slot >= 5 && slot <= 8) {
                return false;
            }

            // prevent dropping non-fuel items
            if (clickEvent.isDropAction()) {
                ItemStack cursorStack = PlayerUtils.getCursorStack(clickEvent.player());
                return fuel.isFuel(cursorStack);
            }

            return true;
        });

        config.allow(ProtectionTypes.USE_BLOCK, (entity, pos) -> {
            onUseBlock(entity, pos);

            return false;
        });

        config.allow(ProtectionTypes.ENTITY_ITEM_DROP, (entity, itemEntity) -> fuel.isFuel(itemEntity.getStack()));
    }

    public void register(HookRegistrar hooks) {
        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, this::onSpawnLocation);

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DEATH, (entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                onDeath(player);
            }

            return true;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                onUseEntity(serverPlayer, hand, entity);
            }

            return ActionResult.FAIL;
        });

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (source.isOf(DamageTypes.FREEZE) && amount < Float.MAX_VALUE) {
                entity.damage(entity.getDamageSources().freeze(), Float.MAX_VALUE);
                return false;
            }

            return true;
        });
    }

    public void configureBaseRegionEvents(CollisionDetector collisions, PlayerMovementObserver observer) {
        for (var entry : args.baseManager().getBases().entrySet()) {
            CCBase base = entry.getValue();
            Collider bounds = base.getBounds();

            collisions.add(bounds);

            Team team = entry.getKey();

            observer.whenEntering(bounds, player -> onEnterBaseOf(player, team));
            observer.whenLeaving(bounds, player -> onLeaveBaseOf(player, team));
        }
    }

    private void onUseEntity(ServerPlayerEntity player, Hand hand, Entity entity) {
        ItemStack stack = player.getStackInHand(hand);
        if (!args.fuel().isFuel(stack)) return;

        Team team = args.baseManager().getEntityTeam(entity).orElse(null);
        if (team == null || !teamManager.isTeamMember(player, team)) return;

        CCBase base = args.baseManager().getBase(team).orElseThrow();
        BlockPos pos = base.getCampfirePos();

        args.fuelListener().onAddFuel(player, pos, team, stack);
    }

    private void onUseBlock(Entity entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayerEntity player)) return;

        BlockState state = entity.getWorld().getBlockState(pos);
        if (!state.isIn(BlockTags.CAMPFIRES)) return;

        ItemStack stack = getHeldFuel(player);
        if (stack == null || stack.isEmpty()) return;

        Team team = args.baseManager().getCampfireTeam(pos).orElse(null);
        if (team == null || !teamManager.isTeamMember(player, team)) return;

        args.fuelListener().onAddFuel(player, pos, team, stack);
    }

    @Nullable
    private ItemStack getHeldFuel(ServerPlayerEntity player) {
        CCFuel fuel = args.fuel();
        ItemStack stack = player.getMainHandStack();

        if (fuel.isFuel(stack)) return stack;

        if (!stack.isEmpty()) return null;

        stack = player.getOffHandStack();

        if (fuel.isFuel(stack)) return stack;

        return null;
    }

    private void onSpawnLocation(PlayerSpawnLocationCallback.LocationData data) {
        if (data.isJoin()) return;

        ServerPlayerEntity player = data.getPlayer();

        Team team = teamManager.getTeam(player).orElse(null);
        if (team == null) return;

        PositionRotation spawn = spawnAccess.getSpawn(team);
        if (spawn == null) return;

        data.setPosition(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()));
        data.setYaw(spawn.getYaw());
        data.setPitch(spawn.getPitch());

        args.kitManager().giveItems(player);
    }

    private void onDeath(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        CCFuel fuel = args.fuel();

        // remove non-fuel items so that they won't be dropped
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);

            if (fuel.isFuel(stack)) continue;

            inventory.removeStack(i);
        }
    }

    private void onEnterBaseOf(ServerPlayerEntity player, Team team) {
        if (teamManager.isTeamMember(player, team)) return;

        // the player is in the base of another team
        var name = translations.translateText(player, team.getKey().getTranslationKey())
                .formatted(team.getKey().colorFormat());

        var msg = Text.literal("⚠")
                .append(translations.translateText(player, "game.ap2.cozy_campfire.base_of", name))
                .append("⚠").styled(style -> style.withColor(0xff0000));

        player.sendMessage(msg, true);
        player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_LAND, SoundCategory.PLAYERS, 0.5f, 1.2f);
    }

    private void onLeaveBaseOf(ServerPlayerEntity player, Team team) {
        if (teamManager.isTeamMember(player, team)) return;

        // player leaves the base of another team
        player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_LAND, SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    public record Args(CCFuel fuel, CCBaseManager baseManager, CCKitManager kitManager, CCFuelListener fuelListener) {}
}
