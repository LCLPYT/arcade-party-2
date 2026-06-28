package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.challenge.*
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape.WithHeight
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape.WithRadius
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import java.util.*

class GuessItManager(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    private val random: Random,
    blockShape: BlockShape,
    modifier: WorldModifier,
    soundSubtitles: SoundSubtitles,
    debugController: DebugController,
    mannequinUuids: IndexedSet<UUID>,
    dynamicEntities: DynamicEntityModifier,
) {
    private val challenges = HashMap<String, Challenge>()
    private val queue = ArrayList<Challenge>()
    private val priority = ArrayList<ChallengeInit>()

    init {
        require(blockShape is WithRadius) { "Stage with radius is required" }
        require(blockShape is WithHeight) { "Stage with height is required" }

        val display = GuessItDisplay(world, modifier, blockShape)

        registerChallenge(MathsChallenge(gameHandle, random))
        registerChallenge(DayTimeChallenge(gameHandle, world, random, blockShape, dynamicEntities))
        registerChallenge(MobCountSingleChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids))
        registerChallenge(MobCountMultiChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids))
        registerChallenge(DistinctMobCountChallenge(gameHandle, world, random, blockShape, modifier, mannequinUuids))
        registerChallenge(SoundChallenge(gameHandle, world, random, soundSubtitles))
        registerChallenge(CakeBitesChallenge(gameHandle, world, random, blockShape, modifier, dynamicEntities))
        registerChallenge(PotionTypeChallenge(gameHandle, random, display))
        registerChallenge(FoodAmountChallenge(gameHandle, random, display))
        registerChallenge(ArmorTrimChallenge(gameHandle, world, random, blockShape, modifier))
        registerChallenge(
            BlockCountChallenge(
                gameHandle,
                random,
                blockShape,
                modifier,
                debugController
            )
        )
        registerChallenge(RecordChallenge(gameHandle, world, random, display))
        registerChallenge(AreaChallenge(gameHandle, world, random, blockShape, modifier))
        registerChallenge(MinecartChallenge(gameHandle, world, random, blockShape, modifier))
    }

    private fun registerChallenge(challenge: Challenge) {
        check(!challenges.containsKey(challenge.id())) { "Duplicate challenge id " + challenge.id() }

        challenges[challenge.id()] = challenge
    }

    fun nextChallenge(): ChallengeInit {
        if (!priority.isEmpty()) {
            return priority.removeFirst()
        }

        if (queue.isEmpty()) {
            check(!challenges.isEmpty()) { "No challenges registered" }

            queue.addAll(challenges.values)
            queue.shuffle(random)
        }

        return ChallengeInit(queue.removeFirst(), null)
    }

    fun pushChallenge(challenge: Challenge, init: Any?) {
        priority.add(ChallengeInit(challenge, init))
    }

    fun getChallenges(): Collection<Challenge> =
        challenges.values.toList()

    data class ChallengeInit(val challenge: Challenge, val init: Any?)
}
