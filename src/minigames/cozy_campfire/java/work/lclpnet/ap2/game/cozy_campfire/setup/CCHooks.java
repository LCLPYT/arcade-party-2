package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.game.team.TeamSpawnAccess;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver;
import work.lclpnet.gaco.ds.Collider;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.prot.ProtectionConfig;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.util.PlayerReset;

import static work.lclpnet.ap2.game.cozy_campfire.CozyCampfireInstance.MOVEMENT_SPEED;

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
            if (entity instanceof ServerPlayer player) {
                return participants.isParticipating(player) && !baseManager.isInBase(player);
            }

            return entity instanceof Boat;  // allow damaging boats
        });

        config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
            if (!(entity instanceof ServerPlayer player)) return false;

            return fuel.isFuel(player, pos);
        });

        config.allow(ProtectionTypes.BLOCK_ITEM_DROP, (world, blockPos, itemStack) -> fuel.isFuel(itemStack));

        config.allow(ProtectionTypes.DROP_ITEM, (player, slot, inInventory) -> {
            if (inInventory || slot < 0 || slot > 8) return true;

            // drop hot-bar item via the drop key while not in inventory
            ItemStack stack = player.getInventory().getItem(slot);

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

        config.allow(ProtectionTypes.ENTITY_ITEM_DROP, (entity, itemEntity) -> fuel.isFuel(itemEntity.getItem()));

        config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK, (player, obj) -> obj.getItemInHand().is(Items.LADDER));
        config.allow(ProtectionTypes.PLACE_BLOCKS, (entity, blockPos) -> true);  // filter with hook in ::register
    }

    public void register(HookRegistrar hooks) {
        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, this::onSpawnLocation);

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DEATH, (entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                onDeath(player);
            }

            return true;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                onUseEntity(serverPlayer, hand, entity);
            }

            return InteractionResult.FAIL;
        });

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (source.is(DamageTypes.FREEZE) && amount < Float.MAX_VALUE && entity.level() instanceof ServerLevel world) {
                entity.hurtServer(world, entity.damageSources().freeze(), Float.MAX_VALUE);
                return false;
            }

            return true;
        });

        hooks.registerHook(BlockModificationHooks.PLACE_BLOCK, (world, pos, entity, state)
                -> !state.is(Blocks.LADDER));
    }

    public void configureBaseRegionEvents(CollisionDetector collisions, PlayerMovementObserver observer) {
        for (var entry : args.baseManager().getBases().entrySet()) {
            CCBase base = entry.getValue();
            Collider bounds = base.bounds();

            collisions.add(bounds);

            Team team = entry.getKey();

            observer.whenEntering(bounds, player -> onEnterBaseOf(player, team));
            observer.whenLeaving(bounds, player -> onLeaveBaseOf(player, team));
        }
    }

    private void onUseEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
        ItemStack stack = player.getItemInHand(hand);
        if (!args.fuel().isFuel(stack)) return;

        Team team = args.baseManager().getEntityTeam(entity).orElse(null);
        if (team == null || !teamManager.isTeamMember(player, team)) return;

        CCBase base = args.baseManager().getBase(team).orElseThrow();
        BlockPos pos = base.campfirePos();

        args.fuelListener().onAddFuel(player, pos, team, stack);
    }

    private void onUseBlock(Entity entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayer player)) return;

        BlockState state = entity.level().getBlockState(pos);
        if (!state.is(BlockTags.CAMPFIRES)) return;

        ItemStack stack = getHeldFuel(player);
        if (stack == null || stack.isEmpty()) return;

        Team team = args.baseManager().getCampfireTeam(pos).orElse(null);
        if (team == null || !teamManager.isTeamMember(player, team)) return;

        args.fuelListener().onAddFuel(player, pos, team, stack);
    }

    @Nullable
    private ItemStack getHeldFuel(ServerPlayer player) {
        CCFuel fuel = args.fuel();
        ItemStack stack = player.getMainHandItem();

        if (fuel.isFuel(stack)) return stack;

        if (!stack.isEmpty()) return null;

        stack = player.getOffhandItem();

        if (fuel.isFuel(stack)) return stack;

        return null;
    }

    private void onSpawnLocation(PlayerSpawnLocationCallback.LocationData data) {
        if (data.isJoin()) return;

        ServerPlayer player = data.getPlayer();

        Team team = teamManager.getTeam(player).orElse(null);
        if (team == null) return;

        PositionRotation spawn = spawnAccess.getSpawn(team);
        if (spawn == null) return;

        data.setPosition(new Vec3(spawn.x(), spawn.y(), spawn.z()));
        data.setYaw(spawn.getYaw());
        data.setPitch(spawn.getPitch());

        args.kitManager().giveItems(player);
        PlayerReset.modifyWalkSpeed(player, MOVEMENT_SPEED);
    }

    private void onDeath(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        CCFuel fuel = args.fuel();

        // remove non-fuel items so that they won't be dropped
        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            ItemStack stack = inventory.getItem(i);

            if (fuel.isFuel(stack)) continue;

            inventory.removeItemNoUpdate(i);
        }
    }

    private void onEnterBaseOf(ServerPlayer player, Team team) {
        if (teamManager.isTeamMember(player, team)) return;

        // the player is in the base of another team
        var name = translations.translateText(player, team.key().getTranslationKey())
                .styled(style -> style.withColor(team.key().color()));

        var msg = Component.literal("⚠")
                .append(translations.translateText(player, "game.ap2.cozy_campfire.base_of", name))
                .append("⚠").withStyle(style -> style.withColor(0xff0000));

        player.displayClientMessage(msg, true);
        player.playNotifySound(SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    private void onLeaveBaseOf(ServerPlayer player, Team team) {
        if (teamManager.isTeamMember(player, team)) return;

        // player leaves the base of another team
        player.playNotifySound(SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    public record Args(CCFuel fuel, CCBaseManager baseManager, CCKitManager kitManager, CCFuelListener fuelListener) {}
}
