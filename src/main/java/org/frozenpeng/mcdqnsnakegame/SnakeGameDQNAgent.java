package org.frozenpeng.mcdqnsnakegame;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnakeGameDQNAgent {

    private static final int BOARD_WIDTH = 13;
    private static final int BOARD_HEIGHT = 13;
    private static final int STATE_CHANNELS = 3; // Empty, Snake, Food
    private static final int ACTION_SIZE = 4; // Up, Down, Left, Right
    private static final double GAMMA = 0.95; // Discount factor
    private static final double EPSILON = 1.0; // Exploration rate
    private static final double EPSILON_MIN = 0.01;
    private static final double EPSILON_DECAY = 0.9999;
    private static final int BATCH_SIZE = 32;
    private static final int REPLAY_MEMORY_SIZE = 10000;

    private MultiLayerNetwork model;
    private Random random;
    private List<Experience> replayMemory;
    private double epsilon;

    public SnakeGameDQNAgent() {
        this.model = buildModel();
        this.random = new Random();
        this.replayMemory = new ArrayList<>();
        this.epsilon = EPSILON;
    }

    private MultiLayerConfiguration buildModelConfiguration() {
        int stateSize = 8; // Number of features in your state representation from getStateInfo()
        int hiddenLayerSize = 64; // Size of the hidden layer

        return new NeuralNetConfiguration.Builder()
                .seed(123) // Random seed for reproducibility
                .weightInit(WeightInit.XAVIER) // Weight initialization method
                .updater(new Adam(0.001)) // Adam optimizer with learning rate 0.001
                .list()
                .layer(new DenseLayer.Builder()
                        .nIn(stateSize) // Input size should match the size of your state representation
                        .nOut(hiddenLayerSize)
                        .activation(Activation.RELU) // ReLU activation function for hidden layer
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MSE) // Mean Squared Error loss
                        .nIn(hiddenLayerSize) // Input size from previous layer
                        .nOut(ACTION_SIZE) // Output size should match the number of actions (ACTION_SIZE)
                        .activation(Activation.IDENTITY) // Identity activation function for output (Q-values)
                        .build())
                .build();
    }

    private MultiLayerNetwork buildModel() {
        MultiLayerConfiguration config = buildModelConfiguration();
        MultiLayerNetwork model = new MultiLayerNetwork(config);
        model.init();
        return model;
    }

    public Direction getAction(INDArray state) {
        if (random.nextDouble() <= epsilon) {
            return Direction.values()[random.nextInt(ACTION_SIZE)];
        } else {
            INDArray output = model.output(state);
            int actionIndex = Nd4j.argMax(output, 1).getInt(0);
            return Direction.values()[actionIndex];
        }
    }

    public void remember(INDArray state, Direction action, double reward, INDArray nextState, boolean done) {
        replayMemory.add(new Experience(state, action, reward, nextState, done));
        if (replayMemory.size() > REPLAY_MEMORY_SIZE) {
            replayMemory.remove(0);
        }
    }

    public void replay() {
        if (replayMemory.size() < BATCH_SIZE) return;

        List<Experience> miniBatch = new ArrayList<>();
        for (int i = 0; i < BATCH_SIZE; i++) {
            int index = random.nextInt(replayMemory.size());
            miniBatch.add(replayMemory.get(index));
        }

        for (Experience experience : miniBatch) {
            INDArray target = model.output(experience.state);
            int actionIndex = experience.action.ordinal();

            if (experience.done) {
                target.putScalar(new int[]{0, actionIndex}, experience.reward);
            } else {
                double futureReward = experience.reward + GAMMA * model.output(experience.nextState).maxNumber().doubleValue();
                target.putScalar(new int[]{0, actionIndex}, futureReward);
            }

            model.fit(experience.state, target);
        }

        if (epsilon > EPSILON_MIN) {
            epsilon *= EPSILON_DECAY;
        }
    }

//    public INDArray getStateFromGame(Main gameInstance) {
//        INDArray state = Nd4j.zeros(1, STATE_CHANNELS, BOARD_HEIGHT, BOARD_WIDTH);
//        for (int x = 0; x < BOARD_WIDTH; x++) {
//            for (int z = 0; z < BOARD_HEIGHT; z++) {
//                Material blockType = gameInstance.getBlockTypeAt(x, z);
//                if (blockType == Material.BLACK_CONCRETE) {
//                    state.putScalar(new int[]{0, 0, z, x}, 1); // Empty space
//                } else if (blockType == Material.GREEN_CONCRETE || blockType == Material.LIME_CONCRETE) {
//                    state.putScalar(new int[]{0, 1, z, x}, 1); // Snake
//                } else if (blockType == Material.RED_CONCRETE) {
//                    state.putScalar(new int[]{0, 2, z, x}, 1); // Food
//                }
//            }
//        }
//        return state;
//    }

    public void runTraining(Main gameInstance, int episodes) {
        trainEpisode(gameInstance, 1, episodes);
    }

    private void trainEpisode(Main gameInstance, int current_episode, int episodes) {
        gameInstance.startSnakeGame(null);


        // Schedule a task to run every TICK_INTERVAL ticks
        int TICK_INTERVAL = 1; // Adjust this as needed
        BukkitTask task = new BukkitRunnable() {
            double totalReward = 0;
            INDArray state = gameInstance.getStateInfo();

            @Override
            public void run() {
                if (!gameInstance.isGameOver()) {
                    Direction action = getAction(state);
                    gameInstance.moveSnake(action);
                    gameInstance.tick();

                    INDArray nextState = gameInstance.getStateInfo();
                    double reward = calculateReward(gameInstance);
                    boolean done = gameInstance.isGameOver();

                    remember(state, action, reward, nextState, done);
                    replay();

                    state = nextState;
                    totalReward += reward;
                } else {
                    Bukkit.broadcastMessage(String.format("Episode: %d, Total Reward: %.3s, Epsilon: %.4s", current_episode, totalReward, epsilon));
                    if (current_episode < episodes) {
                        trainEpisode(gameInstance, current_episode + 1, episodes);
                    }
                    cancel(); // End the task when the episode is over
                }
            }
        }.runTaskTimer(gameInstance, 0, TICK_INTERVAL);

        // Print episode information after the task completes
        task.getTaskId();
    }


    private double calculateReward(Main gameInstance) {
        if (gameInstance.isGameOver()) {
            return -10; // Penalty for game over
        } else if (gameInstance.eaten_food) {
            gameInstance.eaten_food = false;
            return 10; // Reward for eating food
        } else {
            return -0.1; // Small penalty for each move to encourage efficiency
        }
    }

    private class Experience {
        INDArray state;
        Direction action;
        double reward;
        INDArray nextState;
        boolean done;

        Experience(INDArray state, Direction action, double reward, INDArray nextState, boolean done) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.nextState = nextState;
            this.done = done;
        }
    }


    public double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }


    public void saveModel(String filename) {
        try {
            File locationToSave = new File(filename);

            // Create parent directories if they don't exist
            if (!locationToSave.getParentFile().exists()) {
                locationToSave.getParentFile().mkdirs();
            }

            ModelSerializer.writeModel(model, locationToSave, true);
            System.out.println("Model saved successfully to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving model to file " + filename);
            e.printStackTrace();
        }
    }
    public void loadModel(String filename) {
        try {
            File locationToLoad = new File(filename);

            // Check if file exists before attempting to load
            if (!locationToLoad.exists()) {
                System.err.println("Model file not found: " + filename);
                return;
            }

            model = ModelSerializer.restoreMultiLayerNetwork(locationToLoad);
            System.out.println("Model loaded successfully from: " + filename);
        } catch (IOException e) {
            System.err.println("Error loading model from file " + filename);
            e.printStackTrace();
        }
    }

}