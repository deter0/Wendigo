import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class UI {

    private BufferedImage dash;
    private BufferedImage bullet;
    private BufferedImage health;
    private double elapsedTime = 0;
    //fixed position for the UI
    int fixedX = 100;
    int fixedY = 100;
    

    public UI() {
        try {
            // Load UI elements
            dash = ImageIO.read(new File("res/dashUI.png"));
            bullet = ImageIO.read(new File("res/Bullet.png"));
            health = ImageIO.read(new File("res/health.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Update(double deltaTime) {
        // Update the UI
        elapsedTime += deltaTime;

    }

    public void Draw(Graphics2D g) {

        AffineTransform dashTransform = new AffineTransform();
        AffineTransform healthTransform = new AffineTransform();
        dashTransform.scale(2, 2);
        dashTransform.translate(45 , 80);
        healthTransform.scale(0.25, 0.25);
        healthTransform.translate(180 + 180 + 50, 180);



        //Set font for future text
        g.setColor(Color.WHITE);
        g.setFont(Game.font32);

        // Draw the dash image
        g.drawImage(dash, dashTransform, null);
        // if (Game.player.canDash) {
        if (true) {
            g.drawString("Ready", fixedX + 75, fixedY + 120);
        } else {
            // g.drawString("Cooldown " + ((double)(System.currentTimeMillis() - Game.player.lastDashed) / 1000), fixedX + 80, fixedY + 120);
        }
        //Draw bullet image and corrosponding things
        g.drawImage(bullet, fixedX, fixedY + 15, null);
        if (Game.gun.magazine > 0){
            g.drawString("x " + String.valueOf(Game.gun.magazine), fixedX + 75, fixedY + 45);}
        else{
            g.drawString(String.valueOf("Reloading " + ((double)(System.currentTimeMillis() - Game.gun.reloadStart) / 1000)), fixedX + 75, fixedY + 45);
        }

        //Draw health image and corrosponding things
        g.drawImage(health, healthTransform, null);
        g.drawString("x " + String.valueOf(Game.player.health), fixedX + 75, fixedY - 20);
    }
}