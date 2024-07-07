package org.frozenpeng.mcdqnsnakegame;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.nd4j.common.config.ND4JClassLoading;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends JavaPlugin implements Listener {

    public final File modelFile = new File(getDataFolder(), "/model.zip");

    private static final int BOARD_WIDTH = 13;
    private static final int BOARD_HEIGHT = 13;
    private static final int TICK_INTERVAL = 5;

    private List<Location> snakeBody;
    private List<Location> snakePositions;
    private Location foodLocation;
    private Direction currentDirection;
    private Random random;
    private boolean isGameOver;
    private int score;
    private World world;
    private boolean aiControlled;
    private SnakeGameDQNAgent dqnAgent;

    @Override
    public void onEnable() {

        ND4JClassLoading.setNd4jClassloader(this.getClassLoader());

        getServer().getPluginManager().registerEvents(this, this);



        // Initialize your DQN agent
        dqnAgent = new SnakeGameDQNAgent();

        // Optionally load a previously saved model
        dqnAgent.loadModel(modelFile.getAbsolutePath());
    }

    @Override
    public void onDisable() {
        // Example of saving the model
        dqnAgent.saveModel(modelFile.getAbsolutePath());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("snakegame")) {
            Player player = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
            if (player != null) {
                startSnakeGame(player);
                sender.sendMessage("Started Snake game for player: " + player.getName());
            } else {
                sender.sendMessage("No players online to start the Snake game.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("trainsnake")) {
            int episodes = 100; // Default number of episodes
            if (args.length > 0) {
                try {
                    episodes = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid number of episodes. Using default: 100");
                }
            }
            dqnAgent.runTraining(this, episodes);
            sender.sendMessage("Starting " + episodes + " training episodes.");
            return true;
        } else if (command.getName().equalsIgnoreCase("savemodel")) {
            dqnAgent.saveModel(modelFile.getPath());
            sender.sendMessage("Model saved successfully.");
            return true;
        } else if (command.getName().equalsIgnoreCase("loadmodel")) {
            dqnAgent.loadModel(modelFile.getPath());
            sender.sendMessage("Model loaded successfully.");
            return true;
        } else if (command.getName().equalsIgnoreCase("getepsilon")) {
            double epsilon = dqnAgent.getEpsilon();
            sender.sendMessage("Current epsilon value: " + epsilon);
            return true;
        } else if (command.getName().equalsIgnoreCase("setepsilon")) {
            if (args.length > 0) {
                try {
                    double epsilon = Double.parseDouble(args[0]);
                    dqnAgent.setEpsilon(epsilon);
                    sender.sendMessage("Epsilon value set to: " + epsilon);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid epsilon value. Please provide a valid number.");
                }
            } else {
                sender.sendMessage("Please provide an epsilon value.");
            }
            return true;
        }
        return false;
    }


    public void startSnakeGame(Player player) {
        world = player != null ? player.getWorld() : Bukkit.getWorlds().get(0);
        aiControlled = (player == null);

        clearEntities(world);

        snakeBody = new ArrayList<>();
        snakePositions = new ArrayList<>();

        setupGameBoard();

        Location spawnLocation = new Location(world, BOARD_WIDTH / 2, 72, BOARD_HEIGHT / 2);
        snakeBody.add(spawnLocation.clone());
        snakePositions.add(spawnLocation.clone());
        spawnLocation.getBlock().setType(Material.GREEN_CONCRETE);

        random = new Random();
        generateFood();

        currentDirection = aiControlled ? Direction.UP : getDirectionFromPlayer(player);
        isGameOver = false;
        score = 0;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameOver) {
                    if (aiControlled) {
                        // Ensure dqnAgent is not null
                        if (dqnAgent == null) {
                            Bukkit.getLogger().severe("DQN Agent is not initialized!");
                            cancel();
                        }
                    } else if (player != null) {
                        currentDirection = getDirectionFromPlayer(player);
                        moveSnake(currentDirection);
                        tick();
                    }

                } else {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, TICK_INTERVAL);
    }

    private void setupGameBoard() {
        for (int x = -1; x <= BOARD_WIDTH; x++) {
            for (int z = -1; z <= BOARD_HEIGHT; z++) {
                Location location = new Location(world, x, 72, z);
                if (x == -1 || x == BOARD_WIDTH || z == -1 || z == BOARD_HEIGHT) {
                    location.getBlock().setType(Material.AIR);
                } else {
                    location.getBlock().setType(Material.BLACK_CONCRETE);
                }
            }
        }
    }

    public void moveSnake(Direction direction) {
        setupGameBoard();

        snakePositions.get(0).getBlock().setType(Material.BLACK_CONCRETE);

        if (!direction.isOpposite(currentDirection)) {
            currentDirection = direction;
        }

        for (int i = snakeBody.size() - 1; i > 0; i--) {
            Location currentPos = snakeBody.get(i);
            Location nextPos = snakeBody.get(i - 1).clone();
            currentPos.getBlock().setType(Material.BLACK_CONCRETE);
            snakeBody.set(i, nextPos);
            snakePositions.set(i, nextPos.clone());
        }

        Location currentLocation = snakeBody.get(0);
        Location newLocation = currentLocation.clone().add(currentDirection.toVector());
        snakeBody.set(0, newLocation);
        snakePositions.set(0, newLocation.clone());
    }

    private Direction getDirectionFromPlayer(Player player) {
        Vector direction = player.getLocation().getDirection();
        double x = direction.getX();
        double z = direction.getZ();

        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            return z > 0 ? Direction.DOWN : Direction.UP;
        }
    }

    private boolean checkCollisions() {
        Location headLocation = snakePositions.get(0);
        if (headLocation.getX() < 0 || headLocation.getX() >= BOARD_WIDTH ||
                headLocation.getZ() < 0 || headLocation.getZ() >= BOARD_HEIGHT) {
            return true;
        }

        for (Location pos : snakePositions.subList(1, snakePositions.size())) {
            if (pos.getBlock().equals(headLocation.getBlock())) {
                return true;
            }
        }

        return false;
    }

    private void generateFood() {
        Location newFoodLocation;
        do {
            int x = random.nextInt(BOARD_WIDTH - 2) + 1;
            int z = random.nextInt(BOARD_HEIGHT - 2) + 1;
            newFoodLocation = new Location(world, x, 72, z);
        } while (snakePositions.contains(newFoodLocation));

        foodLocation = newFoodLocation;
        foodLocation.getBlock().setType(Material.RED_CONCRETE);
    }

    private void eatFood() {
        Location tail = snakeBody.get(snakeBody.size() - 1);
        Location newTail = tail.clone().subtract(currentDirection.toVector());

        snakeBody.add(newTail);
        snakePositions.add(newTail.clone());
        score++;
    }

    private void clearEntities(World world) {
        if (snakeBody != null) {
            for (Location loc : snakeBody) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        if (foodLocation != null) {
            foodLocation.getBlock().setType(Material.AIR);
        }
    }

    private void endGame() {
        isGameOver = true;
    }

    private void updateDisplay() {
        for (int i = 0; i < snakePositions.size(); i++) {
            Location loc = snakePositions.get(i);
            if (i == 0) {
                loc.getBlock().setType(Material.GREEN_CONCRETE);
            } else {
                loc.getBlock().setType(Material.LIME_CONCRETE);
            }
        }

        foodLocation.getBlock().setType(Material.RED_CONCRETE);
    }


    boolean eaten_food;
    public void tick() {
        if (checkCollisions()) {
            endGame();
            return;
        }

        if (snakePositions.get(0).getBlock().equals(foodLocation.getBlock())) {
            eatFood();
            generateFood();
            eaten_food = true;
        }

        updateDisplay();
    }

    public Material getBlockTypeAt(int x, int z) {
        return world.getBlockAt(x, 72, z).getType();
    }

    public boolean isGameOver() {
        return this.isGameOver;
    }

    public INDArray getStateInfo() {
        Location headLocation = snakePositions.get(0);
        Location appleLocation = foodLocation;

        // Head and apple coordinates
        int headX = headLocation.getBlockX();
        int headZ = headLocation.getBlockZ();
        int appleX = appleLocation.getBlockX();
        int appleZ = appleLocation.getBlockZ();

        // Determine direction of snake's head
        int headDirection;
        if (currentDirection == Direction.UP) {
            headDirection = 0;
        } else if (currentDirection == Direction.DOWN) {
            headDirection = 1;
        } else if (currentDirection == Direction.LEFT) {
            headDirection = 2;
        } else if (currentDirection == Direction.RIGHT) {
            headDirection = 3;
        } else {
            headDirection = -1; // Default or error case
        }

        // Check if directions are blocked
        int leftBlockedType = DirectionBlock(headLocation, Direction.LEFT);
        int rightBlockedType = DirectionBlock(headLocation, Direction.RIGHT);
        int forwardBlockedType = DirectionBlock(headLocation, currentDirection);

        // Create INDArray with shape [1, 8] (1 row, 8 columns)
        INDArray stateArray = Nd4j.create(new double[]{headX, headZ, appleX, appleZ, leftBlockedType, rightBlockedType, forwardBlockedType, headDirection}, new int[]{1, 8});

        return stateArray;
    }



    private int DirectionBlock(Location headLocation, Direction direction) {
        Location nextLocation = headLocation.clone().add(direction.toVector());
        Material blockType = nextLocation.getBlock().getType();

        if (blockType.equals(Material.AIR)) {
            return 0; // Represents AIR
        } else if (blockType.equals(Material.LIME_CONCRETE)) {
            return 1; // Represents LIME_CONCRETE
        } else if (blockType.equals(Material.BLACK_CONCRETE)) {
            return 2; // Represents BLACK_CONCRETE
        } else if (blockType.equals(Material.RED_CONCRETE)) {
            return 3; // Represents RED_CONCRETE
        } else {
            // Handle other block types if necessary
            return -1; // Or another default value indicating unknown block type
        }
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