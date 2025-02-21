package work.lclpnet.ap2.impl.util;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.util.debug.StopWatchImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StopWatchImplTest {

    @Test
    void pop_wait50Ms_sectionIsCreated() throws InterruptedException {
        StopWatchImpl stopWatch = new StopWatchImpl();
        stopWatch.enable();
        stopWatch.start("test");

        Thread.sleep(50);

        stopWatch.stop();

        var sections = stopWatch.getSections();
        assertEquals(1, sections.size());

        var section = sections.getFirst();
        assertEquals("test", section.name());
        assertEquals(5e-2, section.nanoSeconds() * 1e-9, 2e-2);  // tolerance of 20ms deviation
    }
}