package io.opentools.examples.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoinRunnerModelTest {
    @Test
    void recordsDistanceAndEndsOnSecondClick() {
        CoinRunnerModel model = new CoinRunnerModel(7);
        model.start("Ada");
        for (int index = 0; index < 10; index++) {
            model.tick(0.05);
        }
        model.finishByClick();

        assertEquals(CoinRunnerModel.State.FINISHED, model.state());
        assertEquals(CoinRunnerModel.EndReason.SECOND_CLICK, model.endReason());
        assertEquals("Ada", model.playerName());
        assertTrue(model.distanceMeters() > 0);
        assertTrue(model.elapsedSeconds() > 0.49);
    }

    @Test
    void startingAnotherRunResetsTheResult() {
        CoinRunnerModel model = new CoinRunnerModel(11);
        model.start("First");
        model.tick(1.0);
        model.finishByClick();
        model.start("Second");

        assertEquals(CoinRunnerModel.State.RUNNING, model.state());
        assertEquals("Second", model.playerName());
        assertEquals(0, model.distanceMeters());
        assertEquals(0, model.score());
        assertEquals(0, model.coins());
    }
}
