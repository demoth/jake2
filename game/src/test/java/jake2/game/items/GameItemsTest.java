package jake2.game.items;

import org.junit.jupiter.api.Test;

public class GameItemsTest {

    @Test
    public void testLoadFromCsv() {
        var items = GameItems.createGameItemList("/items.csv");
        items.forEach(System.out::println);
    }
}
