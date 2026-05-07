package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Getter @Setter
public final class CustomScoreboardEntry {

    private @Nullable Component display;
    private NumberFormat numberFormat;
    private int score;

    public CustomScoreboardEntry(@Nullable Component display, NumberFormat numberFormat, int score) {
        this.display = display;
        this.numberFormat = numberFormat;
        this.score = score;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CustomScoreboardEntry) obj;
        return Objects.equals(this.display, that.display) &&
                this.score == that.score &&
                Objects.equals(this.numberFormat, that.numberFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(display, score, numberFormat);
    }

    @Override
    public String toString() {
        return "CustomScoreboardEntry[display=%s, score=%d, numberFormat=%s]".formatted(display, score, numberFormat);
    }
}
