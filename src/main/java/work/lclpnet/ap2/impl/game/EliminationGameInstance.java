package work.lclpnet.ap2.impl.game;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;

public abstract class EliminationGameInstance extends DefaultGameInstance {

    private final EliminationDataContainer data = new EliminationDataContainer();

    public EliminationGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        data.eliminated(player);

        var participants = gameHandle.getParticipants().getAsSet();

        if (participants.size() > 1) return;

        var winner = participants.stream().findAny();

        if (winner.isEmpty()) {
            winner = data.getBestPlayer(gameHandle.getServer());
        }

        winner.ifPresent(data::eliminated);  // the winner also has to be tracked

        win(winner.orElse(null));
    }

    @Override
    protected EliminationDataContainer getData() {
        return data;
    }
}
