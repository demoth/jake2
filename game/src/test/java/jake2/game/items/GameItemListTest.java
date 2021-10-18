package jake2.game.items;

import org.junit.Test;

public class GameItemListTest {

    @Test
    public void testLoadFromCsv() {
        var items = GameItemList.createGameItemList("/items.csv");
        items.forEach(System.out::println);
    }
}
