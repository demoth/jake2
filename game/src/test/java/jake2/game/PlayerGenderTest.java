package jake2.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerGenderTest {
    @Test
    void missingOrEmptyGenderIsNeutral() {
        assertGender("", false, true);
        assertGender("\\name\\Player", false, true);
        assertGender("\\gender\\", false, true);
    }

    @Test
    void genderUsesCaseInsensitiveFirstCharacter() {
        assertGender("\\gender\\female", true, false);
        assertGender("\\gender\\FeMaLe", true, false);
        assertGender("\\gender\\F", true, false);
        assertGender("\\gender\\male", false, false);
        assertGender("\\gender\\MaLe", false, false);
        assertGender("\\gender\\M", false, false);
        assertGender("\\gender\\none", false, true);
    }

    @Test
    void entityWithoutClientHasNeitherGenderClassification() {
        GameEntity entity = new GameEntity(1);
        assertFalse(PlayerClient.IsFemale(entity));
        assertFalse(PlayerClient.IsNeutral(entity));
    }

    private static void assertGender(String userinfo, boolean female, boolean neutral) {
        GameEntity entity = new GameEntity(1);
        GamePlayerInfo client = new GamePlayerInfo(0);
        client.pers.userinfo = userinfo;
        entity.setClient(client);

        assertEquals(female, PlayerClient.IsFemale(entity), userinfo);
        assertEquals(neutral, PlayerClient.IsNeutral(entity), userinfo);
    }
}
