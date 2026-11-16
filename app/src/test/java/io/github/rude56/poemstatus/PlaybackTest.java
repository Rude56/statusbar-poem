package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 播放顺序：顺序从第一首开始；随机始终返回有效对象且不连续重复。 */
public class PlaybackTest {

    private static List<PoemItem> sample(int n) {
        List<PoemItem> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new PoemItem("", "诗句" + i, "作者" + i, "出处" + i, true));
        }
        return list;
    }

    @Test
    public void sequentialStartsAtFirstAndWraps() {
        List<PoemItem> list = sample(3);
        PoemLibrary.Playback p = new PoemLibrary.Playback(list, PoemLibrary.ORDER_SEQUENTIAL, 1L);
        assertEquals("诗句0", p.next().text);
        assertEquals("诗句1", p.next().text);
        assertEquals("诗句2", p.next().text);
        assertEquals("诗句0", p.next().text);
    }

    @Test
    public void randomReturnsValidItemsAndNeverRepeatsConsecutively() {
        List<PoemItem> list = sample(6);
        PoemLibrary.Playback p = new PoemLibrary.Playback(list, PoemLibrary.ORDER_RANDOM, 42L);
        String prev = null;
        for (int i = 0; i < 300; i++) {
            PoemItem item = p.next();
            assertNotNull(item);
            assertTrue(list.contains(item));
            if (prev != null) {
                assertFalse("连续重复: " + prev, prev.equals(item.text));
            }
            prev = item.text;
        }
    }

    @Test
    public void randomSingleItemDoesNotLoopForever() {
        PoemLibrary.Playback p = new PoemLibrary.Playback(sample(1), PoemLibrary.ORDER_RANDOM, 7L);
        assertEquals("诗句0", p.next().text);
        assertEquals("诗句0", p.next().text);
    }

    @Test
    public void emptySourceReturnsNull() {
        PoemLibrary.Playback p = new PoemLibrary.Playback(new ArrayList<PoemItem>(), PoemLibrary.ORDER_SEQUENTIAL, 1L);
        assertNull(p.next());
        assertEquals(0, p.size());
    }

    @Test
    public void unknownOrderFallsBackToSequential() {
        assertEquals(PoemLibrary.ORDER_SEQUENTIAL, PoemLibrary.normalizeOrder("nonsense"));
        assertEquals(PoemLibrary.ORDER_SEQUENTIAL, PoemLibrary.normalizeOrder(null));
        assertEquals(PoemLibrary.ORDER_RANDOM, PoemLibrary.normalizeOrder("RANDOM"));
    }
}
