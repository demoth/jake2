package jake2.game;

import org.junit.Test;

import java.util.Arrays;

public class GameItemListTest {

    @Test
    public void testLoadFromCsv() {
        GameItemList actual = new GameItemList("/items.csv");
        Arrays.stream(actual.itemlist).forEach(System.out::println);
    }

}
