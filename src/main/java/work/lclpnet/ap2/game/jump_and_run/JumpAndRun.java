package work.lclpnet.ap2.game.jump_and_run;

import java.util.List;

public class JumpAndRun {

    private final List<OrientedPart> parts;

    public JumpAndRun(List<OrientedPart> parts) {
        this.parts = parts;
    }

    public List<OrientedPart> getParts() {
        return parts;
    }
}
