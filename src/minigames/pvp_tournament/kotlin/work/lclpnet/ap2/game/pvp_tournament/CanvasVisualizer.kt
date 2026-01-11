package work.lclpnet.ap2.game.pvp_tournament

import eu.pb4.mapcanvas.api.core.CanvasColor
import eu.pb4.mapcanvas.api.core.DrawableCanvas
import eu.pb4.mapcanvas.api.core.PlayerCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.pvp_tournament.gen.SkinPlayerIcons
import work.lclpnet.ap2.game.pvp_tournament.gen.Tournament
import work.lclpnet.ap2.game.pvp_tournament.gen.TournamentVisualizer
import work.lclpnet.ap2.util.mojang.SkinFetcher
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.map.MapColorUtil
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.math.max

const val DEBUG_PROGRESSION = true

class CanvasVisualizer(
    val gameHandle: MiniGameHandle,
    val scope: CoroutineScope,
) {
    val playerIcons = SkinPlayerIcons(
        SkinFetcher(
            gameHandle.assetManager.httpClient,
            gameHandle.assetManager.mojangAssetCache,
            SkinFetcher.sharedSkinDirectory(),
            gameHandle.logger,
        )
    )

    val canvas: PlayerCanvas = DrawableCanvas.create().also { canvas ->
        gameHandle.whenDone {
            // temporary fix, until https://github.com/Patbox/map-canvas-api/issues/8 is fixed
            PlayerLookup.all(gameHandle.server).forEach { canvas.removePlayer(it) }

            canvas.destroy()
        }
    }

    val progressionDebugDir = if (DEBUG_PROGRESSION) {
        Files.createTempDirectory("ap2_1v1").also {
            gameHandle.logger.info("Writing progression debugging to $it")
        }
    } else null

    var progressionCounter = 0

    fun preloadPlayerSkins(): List<Job> = gameHandle.participants.map {
        scope.launch { playerIcons.preload(it.gameProfile) }
    }

    suspend fun updateCanvas(tournament: Tournament) {
        val visualizer = TournamentVisualizer(playerIcons)
        val image = visualizer.generateImage(tournament)
        val raw = MapColorUtil.toBytes(image)

        val imgStartX = max(0, (image.width - canvas.width) / 2)
        val imgStartY = max(0, (image.height - canvas.height) / 2)
        val imgEndX = imgStartX + image.width - 2 * imgStartX
        val imgEndY = imgStartY + image.height - 2 * imgStartY

        val canvasX = (canvas.width - image.width) / 2
        val canvasY = (canvas.height - image.height) / 2

        synchronized(canvas) {
            canvas.fill(CanvasColor.CLEAR)

            for (imgY in imgStartY..<imgEndY) {
                for (imgX in imgStartX..<imgEndX) {
                    val canvasX = imgX - imgStartX + canvasX
                    val canvasY = imgY - imgStartY + canvasY

                    canvas.setRaw(
                        canvasX,
                        canvasY,
                        raw[imgY * image.width + imgX]
                    )
                }
            }

            canvas.sendUpdates()
        }

        progressionDebugDir?.let { dir ->
            val visualizer = TournamentVisualizer(playerIcons, scale = 4)
            val svg = visualizer.generateSvg(tournament)
            val id = progressionCounter++

            dir.resolve("$id.svg").writeText(svg)
        }
    }

    fun getStack(): ItemStack = canvas.asStack()

    fun add(player: ServerPlayer) {
        canvas.addPlayer(player)
    }

    fun launchUpdate(tournament: Tournament) {
        // tournament is mutable, update is done on separate thread, therefore pass a copy
        // assume this function is called from the server thread and only the server thread mutates tournament
        val tournamentCopy = tournament.copy()

        scope.launch {
            updateCanvas(tournamentCopy)
        }
    }

    fun preventMovingOfFilledMaps() {
        gameHandle.hooks.registerHook(PlayerInventoryHooks.SWAP_HANDS, PlayerInventoryHooks.SwapHands { player, _ ->
            player.offhandItem.`is`(Items.FILLED_MAP)
        })

        gameHandle.hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, PlayerInventoryHooks.InventoryModify { event ->
            val stack = event.clickedStack()

            stack != null && stack.`is`(Items.FILLED_MAP)
        })

        gameHandle.hooks.registerHook(PlayerInventoryHooks.DROP_ITEM, PlayerInventoryHooks.DropItem { player, i, _ ->
            val slot = player.inventory.getSlot(i)

            slot != null && slot.get().`is`(Items.FILLED_MAP)
        })
    }
}