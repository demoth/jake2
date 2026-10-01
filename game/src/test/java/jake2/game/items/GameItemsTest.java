package jake2.game.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GameItemsTest {

    @Test
    public void testLoadFromCsv() {
        var items = GameItems.createGameItemList("/items.csv");
        assertEquals(41, items.size());
        assertEquals("item_armor_body", items.get(0).classname);
        assertEquals("Body Armor", items.get(0).pickup_name);
        assertEquals("Health", items.get(40).pickup_name);
        for (int i = 0; i < items.size(); i++) {
            assertEquals(i, items.get(i).index);
        }
    }
}
