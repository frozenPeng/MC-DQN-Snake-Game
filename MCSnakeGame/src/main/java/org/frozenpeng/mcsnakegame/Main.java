package org.frozenpeng.mcsnakegame;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends JavaPlugin implements Listener {

    private static final int BOARD_WIDTH = 20;
    private static final int BOARD_HEIGHT = 20;
    private static final int TICK_INTERVAL = 7; // Game tick interval in Minecraft ticks (1 second at 20 ticks per second)
    private static final long TICK_DELAY = 1; // Delay between ticks in seconds

    private List<Location> snakeBody;
    private List<Location> snakePositions;
    private Location foodLocation;
    private Direction currentDirection;
    private Random random;
    private boolean isGameOver;
    private int score;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("snakegame")) {
            Player player = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
            if (player != null) {
                startSnakeGame(player);
            } else {
                sender.sendMessage("No players online to start the Snake game.");
            }
            return true;
        }
        return false;
    }

    private void startSnakeGame(Player player) {
        World world = player.getWorld();

        // Clear existing entities (if any)
        clearEntities(world);

        // Initialize snake data structures
        snakeBody = new ArrayList<>();
        snakePositions = new ArrayList<>();

        // Set up game board
        setupGameBoard(world);

        // Spawn the snake at the center of the world
        Location spawnLocation = new Location(world, BOARD_WIDTH / 2, 72, BOARD_HEIGHT / 2);
        snakeBody.add(spawnLocation.clone());
        snakePositions.add(spawnLocation.clone());
        spawnLocation.getBlock().setType(Material.GREEN_CONCRETE); // Set snake head to green concrete

        // Generate initial food location
        random = new Random(); // Initialize random here
        generateFood();

        // Initialize game state
        currentDirection = getDirectionFromPlayer(player);
        isGameOver = false;
        score = 0;

        // Start the game loop
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameOver) {
                    moveSnake(player); // Move snake based on player's direction
                    tick(); // Process game logic for each tick
                } else {
                    cancel(); // End the game loop if game over
                }
            }
        }.runTaskTimer(this, 0, TICK_INTERVAL);
    }

    private void setupGameBoard(World world) {
        // Set border to black concrete
        for (int x = 0; x < BOARD_WIDTH; x++) {
            for (int z = 0; z < BOARD_HEIGHT; z++) {
                if (x == 0 || x == BOARD_WIDTH - 1 || z == 0 || z == BOARD_HEIGHT - 1) {
                    Location borderLocation = new Location(world, x, 72, z);
                    borderLocation.getBlock().setType(Material.BLACK_CONCRETE);
                }
            }
        }

        // Fill inside with black concrete
        for (int x = 1; x < BOARD_WIDTH - 1; x++) {
            for (int z = 1; z < BOARD_HEIGHT - 1; z++) {
                Location insideLocation = new Location(world, x, 72, z);
                insideLocation.getBlock().setType(Material.BLACK_CONCRETE);
            }
        }
    }


    private void moveSnake(Player player) {
        // Clear previous snake head position
        snakePositions.get(0).getBlock().setType(Material.BLACK_CONCRETE);

        // Determine movement direction based on player's facing direction
        Direction newDirection = getDirectionFromPlayer(player);

        // Update current direction if it's not the opposite of the new direction
        if (!newDirection.isOpposite(currentDirection)) {
            currentDirection = newDirection;
        }

        // Move snake body parts
        for (int i = snakeBody.size() - 1; i > 0; i--) {
            Location currentPos = snakeBody.get(i).clone();
            Location nextPos = snakeBody.get(i - 1).clone();
            currentPos.getBlock().setType(Material.BLACK_CONCRETE); // Clear previous position
            snakeBody.set(i, nextPos);
            snakePositions.set(i, nextPos.clone()); // Update snakePositions as well
        }

        // Move snake head
        Location currentLocation = snakeBody.get(0);
        Location newLocation = currentLocation.clone().add(currentDirection.toVector());
        snakeBody.set(0, newLocation);
        snakePositions.set(0, newLocation.clone()); // Update snakePositions for the head as well
    }




    private Direction getDirectionFromPlayer(Player player) {
        Vector direction = player.getLocation().getDirection();
        double x = direction.getX();
        double z = direction.getZ();

        // Determine the dominant direction
        if (Math.abs(x) > Math.abs(z)) {
            // X direction
            if (x > 0) {
                return Direction.RIGHT;
            } else {
                return Direction.LEFT;
            }
        } else {
            // Z direction
            if (z > 0) {
                return Direction.DOWN;
            } else {
                return Direction.UP;
            }
        }
    }

    private boolean checkCollisions() {
        // Check wall collisions
        Location headLocation = snakePositions.get(0);
        if (headLocation.getX() < 0 || headLocation.getX() >= BOARD_WIDTH ||
                headLocation.getZ() < 0 || headLocation.getZ() >= BOARD_HEIGHT ) {
            return true; // Game over if snake hits wall
        }

        // Check self-collision
        for (Location pos : snakePositions.subList(1, snakePositions.size())) {
            if (pos.getBlock().equals(headLocation.getBlock())) {
                return true; // Game over if snake collides with itself
            }
        }

        return false;
    }

    private void generateFood() {
        World world = snakePositions.get(0).getWorld();
        Location newFoodLocation;

        // Randomly generate food location that doesn't overlap with snake
        do {
            int x = random.nextInt(BOARD_WIDTH - 2) + 1; // Exclude border walls
            int z = random.nextInt(BOARD_HEIGHT - 2) + 1; // Exclude border walls
            newFoodLocation = new Location(world, x, 72, z); // Food at y=72
        } while (snakePositions.contains(newFoodLocation));

        // Spawn food as a block at the generated location
        foodLocation = newFoodLocation;
        foodLocation.getBlock().setType(Material.RED_CONCRETE); // Set food block to red concrete
    }

    private void eatFood() {
        // Increase snake length
        Location tail = snakeBody.get(snakeBody.size() - 1); // Get current tail location
        Location newTail = tail.clone().subtract(currentDirection.toVector()); // Calculate new tail position

        // Add new tail segment at the end of the snake
        snakeBody.add(newTail);
        snakePositions.add(newTail.clone());
    }

    private void clearEntities(World world) {
        // Clear existing entities in the game area (if any)
        if (snakeBody != null) {
            for (Location loc : snakeBody) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    private void endGame() {
        isGameOver = true;
        Bukkit.broadcastMessage("Game Over! Final Score: " + score);
    }

    private void updateScore() {
        score++;
    }


    private void updateDisplay() {
        // Update display of snake body
        for (int i = 0; i < snakePositions.size(); i++) {
            Location loc = snakePositions.get(i);
            if (i == 0) {
                loc.getBlock().setType(Material.GREEN_CONCRETE); // Head of the snake
            } else {
                loc.getBlock().setType(Material.LIME_CONCRETE); // Tail of the snake
            }
        }

        // Update display of food
        foodLocation.getBlock().setType(Material.RED_CONCRETE);
    }

    private void tick() {
        // Check for collisions
        if (checkCollisions()) {
            endGame();
            return;
        }

        // Check if snake ate food
        if (snakePositions.get(0).getBlock().equals(foodLocation.getBlock())) {
            eatFood();
            generateFood(); // Generate new food
            updateScore(); // Update score
        }

        // Update display
        updateDisplay();
    }

}

enum Direction {
    UP(new Vector(0, 0, -1)),
    DOWN(new Vector(0, 0, 1)),
    LEFT(new Vector(-1, 0, 0)),
    RIGHT(new Vector(1, 0, 0));

    private final Vector vector;

    Direction(Vector vector) {
        this.vector = vector;
    }

    public Vector toVector() {
        return vector.clone();
    }

    public boolean isOpposite(Direction other) {
        return this == UP && other == DOWN ||
                this == DOWN && other == UP ||
                this == LEFT && other == RIGHT ||
                this == RIGHT && other == LEFT;
    }
}