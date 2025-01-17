import java.awt.*;

public class Enemy extends GameObject {
    public int x, y;
    private final int RADIUS = 25; // Radius of the red circle
    private int speed = 200; // Speed of the enemy
    public static final int ENEMY_RADIUS = 100; // Radius of the enemy's movement compared to each other.
    public static final int ALTER_SPEED = 50; // Speed of the enemy when they are close to each other.
    public static final int PLAYER_RADIUS = 50; // Radius of the player's movement compared to the enemy.
    private double targetDX = 0; // Target direction X
    private double targetDY = 0; // Target direction Y

    public Enemy(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void Draw(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillOval(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2);

        g.setColor(Color.BLACK);
        g.drawOval(x - ENEMY_RADIUS, y - ENEMY_RADIUS, ENEMY_RADIUS * 2, ENEMY_RADIUS * 2);
    }

    public void Update(double deltaTime) {


        // Check proximity to the player to trigger an attack
        if (Math.sqrt(Math.pow(Game.player.x - x, 2) + Math.pow(Game.player.y - y, 2)) < PLAYER_RADIUS) {
            // Game.player.health--;
        } 

        // Follow the player
        int playerX = Game.player.x;
        int playerY = Game.player.y;

        double dx = playerX - x;
        double dy = playerY - y;

        // Check for nearby enemies and adjust direction
        for (Enemy other : Game.enemies) { // Assuming Game.enemies is a list of all enemies
            if (other != this) {
                double distance = Math.sqrt(Math.pow(other.x - this.x, 2) + Math.pow(other.y - this.y, 2));
                if (distance < ENEMY_RADIUS) {
                    // Move away from the other enemy
                    double repelDX = this.x - other.x;
                    double repelDY = this.y - other.y;
                    double repelLength = Math.sqrt(repelDX * repelDX + repelDY * repelDY);
                    if (repelLength > 0) {
                        dx += (repelDX / repelLength) * ALTER_SPEED;
                        dy += (repelDY / repelLength) * ALTER_SPEED;
                    }
                }
            }
        }

        setTargetDirection(dx, dy);

        // Move the enemy
        x += targetDX * speed * deltaTime;
        y += targetDY * speed * deltaTime;
    }

    public void setTargetDirection(double dx, double dy) {
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length > 0) {
            this.targetDX = dx / length;
            this.targetDY = dy / length;
        } else {
            this.targetDX = 0;
            this.targetDY = 0;
        }
    }

    // Getter methods
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getRadius() {
        return RADIUS;
    }
}
