import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;


import javax.imageio.ImageIO;

public class Weapon {
    private int range;
    private int fireRate;
    private int dmg;
    private Player owner; // Reference to the player
    private ArrayList<Projectile> projectiles; // List of projectiles
    private long lastFireTime = 0; // Track time since the last shot
    private BufferedImage bullet;
    public int magazine = 30;
    private boolean canShoot;
    public long reloadStart;
    private long reloadTime = 2500;



    public Weapon(int range, int fireRate, int dmg, Player owner) {
        this.range = range;
        this.fireRate = fireRate;
        this.dmg = dmg;
        this.owner = owner;
        this.projectiles = new ArrayList<>();

        // Read the image
		try {
			bullet = ImageIO.read(new File("res/Bullet.png"));
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public void Update(double deltaTime) {

        if (canShoot){
        // Handle firing
        if (Game.IsMouseDown(MouseEvent.BUTTON1) && System.currentTimeMillis() - lastFireTime >= 1000 / fireRate) {
            fire();
            magazine--;
            lastFireTime = System.currentTimeMillis();
        }
        if (magazine == 0){
            canShoot = false;
            reloadStart = System.currentTimeMillis();        
            
        } 
    }
       else if (!canShoot){
        if (System.currentTimeMillis() - reloadStart >= reloadTime){
            canShoot = true;
            magazine = 30;
        }
    }

        // Update projectiles
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile p = projectiles.get(i);
            p.Update(deltaTime);
            if (p.isOutOfRange()) {
                projectiles.remove(i);
                i--; // Adjust index after removal
            }
        }
    }

    public void Draw(Graphics2D g) {
        // Draw all projectiles
        for (Projectile p : projectiles) {
            p.Draw(g);
        }
    }

    private void fire() {
        // Create a new projectile originating at the player's position
        int startX = owner.x; // Center of the player
        int startY = owner.y; // Center of the player
    
        // Use the owner's reflect property to determine direction
        projectiles.add(new Projectile(startX, startY, range, dmg, owner.reflect));
    }
    
    // Inner class for projectiles
    private class Projectile {
        private int x, y;
        private int range;
        private int dmg;
        private int startX; // Starting position to calculate range
        private final int SPEED = 1500; // Speed of the projectile
        private boolean reflect; // true = left, false = right
    
        public Projectile(int x, int y, int range, int dmg, boolean reflect) {
            this.x = x;
            this.y = y;
            this.range = range;
            this.dmg = dmg;
            this.startX = x;
            this.reflect = reflect; // Correctly assign reflect
        }
    
        public void Update(double deltaTime) {
            // Move the projectile based on the direction
            if (reflect) {
                x -= SPEED * deltaTime; // Move left
            } else {
                x += SPEED * deltaTime; // Move right
            }
        }
    
        public void Draw(Graphics2D g) {
            if (reflect) {
                // Create an AffineTransform to flip the image horizontally
                AffineTransform transform = new AffineTransform();
                // Translate to the position first
                transform.translate(x + bullet.getWidth() - 35, y - 27);
                // Scale horizontally by -1 to flip the image
                transform.scale(-1, 1);
                g.drawImage(bullet, transform, null);
            } else {
                // Draw the projectile adjusting so its centered correctly and coming out of the gun
                g.drawImage(bullet, x - 5, y - 27, null);
            }
        }
    
        public boolean isOutOfRange() {
            // Check if the projectile has exceeded its range
            return Math.abs(x - startX) > range;
        }
    }
}    