package jake2.game.items;

import jake2.game.GameItems;
import org.junit.Test;

public class GameItemsTest {

    @Test
    public void testLoadFromCsv() {
        var items = GameItems.createGameItemList("/items.csv");
        items.forEach(System.out::println);
    }
}
