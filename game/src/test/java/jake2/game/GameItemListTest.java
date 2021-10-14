package jake2.game;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class GameItemListTest {

    @Test
    public void testLoadFromCsv() {
        GameItemList expected = new GameItemList();
        GameItemList actual = new GameItemList("/items.csv");
        for (int i = 0; i < actual.itemlist.length; i++) {
            assertEquals(expected.itemlist[i], actual.itemlist[i]);
        }
        Arrays.stream(actual.itemlist).forEach(System.out::println);
    }

}
