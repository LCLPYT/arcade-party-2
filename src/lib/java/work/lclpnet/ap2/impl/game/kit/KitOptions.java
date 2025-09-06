package work.lclpnet.ap2.impl.game.kit;

public record KitOptions(int mainItemSlot, int kitSelectorSlot) {

    public static final KitOptions DEFAULT = new KitOptions(0, 4);

    public KitOptions withKitSelectorSlot(int kitSelectorSlot) {
        return new KitOptions(mainItemSlot, kitSelectorSlot);
    }

    public KitOptions withMainItemSlot(int mainItemSlot) {
        return new KitOptions(mainItemSlot, kitSelectorSlot);
    }
}
