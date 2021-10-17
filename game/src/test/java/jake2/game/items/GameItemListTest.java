package jake2.game.items;

import jake2.game.GameItemList;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class GameItemListTest {

    @Test
    public void testLoadFromCsv() {
        GameItemList actual = new GameItemList("/items.csv");
        Arrays.stream(actual.itemlist).forEach(System.out::println);
    }

    @Test
    public void testMigration() {
        GameItemList expected = new GameItemList();
        GameItemList actual = new GameItemList("/items.csv");
        for (int i = 0; i < actual.itemlist.length; i++) {
            assertEquals(expected.itemlist[i], actual.itemlist[i]);
        }
        Arrays.stream(actual.itemlist).forEach(System.out::println);
    }

}
