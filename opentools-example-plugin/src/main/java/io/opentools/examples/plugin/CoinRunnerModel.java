package io.opentools.examples.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class CoinRunnerModel {
    enum State {
        READY,
        RUNNING,
        FINISHED
    }

    enum EndReason {
        SECOND_CLICK,
        COLLISION
    }

    enum EntityKind {
        COIN,
        OBSTACLE
    }

    record Entity(EntityKind kind, double x, double y, double size) {
    }

    static final double WORLD_WIDTH = 1000;
    static final double WORLD_HEIGHT = 460;
    static final double PLAYER_X = 150;
    static final double PLAYER_SIZE = 34;
    static final double GROUND_Y = 380;
    private static final double GRAVITY = 1_650;
    private static final double JUMP_SPEED = 570;
    private static final double ENTITY_SIZE = 38;

    private final List<Entity> entities = new ArrayList<>();
    private final Random random;
    private State state = State.READY;
    private EndReason endReason;
    private String playerName = "Runner";
    private double playerY;
    private double velocityY;
    private double distanceMeters;
    private double score;
    private int coins;
    private double elapsedSeconds;
    private double nextSpawnSeconds;

    CoinRunnerModel(long seed) {
        random = new Random(seed);
    }

    void start(String playerName) {
        this.playerName = playerName == null || playerName.isBlank() ? "Runner" : playerName.strip();
        state = State.RUNNING;
        endReason = null;
        playerY = 0;
        velocityY = 0;
        distanceMeters = 0;
        score = 0;
        coins = 0;
        elapsedSeconds = 0;
        nextSpawnSeconds = 0.55;
        entities.clear();
    }

    void jump() {
        if (state == State.RUNNING && playerY <= 0.01) {
            velocityY = JUMP_SPEED;
        }
    }

    void finishByClick() {
        if (state == State.RUNNING) {
            finish(EndReason.SECOND_CLICK);
        }
    }

    void tick(double deltaSeconds) {
        if (state != State.RUNNING) {
            return;
        }
        double delta = Math.max(0, Math.min(deltaSeconds, 0.05));
        elapsedSeconds += delta;
        double speed = 230 + Math.min(320, distanceMeters * 0.22);
        distanceMeters += speed * delta / 10.0;
        score += speed * delta * 0.08;

        velocityY -= GRAVITY * delta;
        playerY = Math.max(0, playerY + velocityY * delta);
        if (playerY == 0 && velocityY < 0) {
            velocityY = 0;
        }

        for (int index = entities.size() - 1; index >= 0; index--) {
            Entity entity = entities.get(index);
            double movedX = entity.x() - speed * delta;
            Entity moved = new Entity(entity.kind(), movedX, entity.y(), entity.size());
            entities.set(index, moved);
            if (!collides(moved)) {
                continue;
            }
            entities.remove(index);
            if (moved.kind() == EntityKind.COIN) {
                coins++;
            } else {
                finish(EndReason.COLLISION);
                return;
            }
        }
        entities.removeIf(entity -> entity.x() < -80);

        nextSpawnSeconds -= delta;
        if (nextSpawnSeconds <= 0) {
            spawnEntity();
            nextSpawnSeconds = Math.max(0.42, 0.82 - distanceMeters / 1_800);
        }
    }

    private void spawnEntity() {
        boolean obstacle = random.nextDouble() < Math.min(0.58, 0.34 + distanceMeters / 3_000);
        if (obstacle) {
            entities.add(new Entity(EntityKind.OBSTACLE, WORLD_WIDTH + 40, 0, ENTITY_SIZE));
        } else {
            double height = random.nextBoolean() ? 68 : 132;
            entities.add(new Entity(EntityKind.COIN, WORLD_WIDTH + 40, height, 28));
        }
    }

    private boolean collides(Entity entity) {
        double horizontal = Math.abs(entity.x() - PLAYER_X);
        if (horizontal > (PLAYER_SIZE + entity.size()) / 2) {
            return false;
        }
        if (entity.kind() == EntityKind.COIN) {
            return Math.abs(entity.y() - (playerY + 28)) < 38;
        }
        return playerY < 40;
    }

    private void finish(EndReason reason) {
        state = State.FINISHED;
        endReason = reason;
        velocityY = 0;
    }

    State state() {
        return state;
    }

    EndReason endReason() {
        return endReason;
    }

    String playerName() {
        return playerName;
    }

    double playerY() {
        return playerY;
    }

    double distanceMeters() {
        return distanceMeters;
    }

    double score() {
        return score + coins * 100;
    }

    int coins() {
        return coins;
    }

    double elapsedSeconds() {
        return elapsedSeconds;
    }

    List<Entity> entities() {
        return List.copyOf(entities);
    }
}
