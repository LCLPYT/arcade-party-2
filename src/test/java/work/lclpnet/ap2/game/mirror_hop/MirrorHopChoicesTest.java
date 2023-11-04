package work.lclpnet.ap2.game.mirror_hop;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MirrorHopChoicesTest {

    private static final Logger logger = LoggerFactory.getLogger(MirrorHopChoicesTest.class);

    @Test
    void from() {
        var choices = MirrorHopChoices.from(new JSONArray("""
                [
                  [
                    [[-3,63,8],[-1,63,10]],
                    [[2,63,8],[4,63,10]]
                  ],
                  [
                    [[-3,63,13],[-1,63,15]],
                    [[2,63,13],[4,63,15]]
                  ],
                  [
                    [[-3,63,18],[-1,63,20]],
                    [[2,63,18],[4,63,20]]
                  ]
                ]
                """), logger);

        var choiceList = choices.getChoices();
        assertEquals(3, choiceList.size());

        for (int i = 0; i < 3; i++) {
            assertEquals(2, choiceList.get(i).platforms().size());
        }
    }
}