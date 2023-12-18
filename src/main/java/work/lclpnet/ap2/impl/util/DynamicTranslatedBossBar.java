package work.lclpnet.ap2.impl.util;

import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;

public class DynamicTranslatedBossBar {

    private final TranslatedBossBar bossBar;
    private String translationKey;
    private Object[] arguments;

    public DynamicTranslatedBossBar(TranslatedBossBar bossBar, String translationKey, Object[] arguments) {
        this.bossBar = bossBar;
        this.translationKey = translationKey;
        this.arguments = arguments;
    }

    public void setTranslationKey(String translationKey) {
        this.translationKey = translationKey;
        update();
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
        update();
    }

    public void setArgument(int i, Object argument) {
        this.arguments[i] = argument;
        update();
    }

    private void update() {
        bossBar.setTitle(translationKey, arguments);
    }
}
