import java.awt.*;

public class GameObject {
    public Vector2 position = new Vector2();
    public Vector2 velocity = new Vector2();
    public Vector2 size = new Vector2();
    public boolean isDrawn = true;
    public boolean isUpdated = true;
    public double mass = 10.0;
    public double restitution = 0.6;
    public double frictionCoefficient = 0.3;

    public void Draw(Graphics2D g) { }
    public void Update(double deltaTime) { }

    public void DrawOutline(Graphics2D g) {
        g.setColor(Color.RED);
        GG.drawRect(this.position, this.size);

        if (this.velocity.magnitude() > 0) {
            Vector2 centre = this.position.add(this.size.scale(0.5));
            Vector2 velocityDirection = this.velocity.normalize().scale(20.0);
    
            g.setColor(Color.GREEN);
            GG.drawLine(centre, centre.add(velocityDirection));
        }
    }

    public Rectangle GetRect() {
        return new Rectangle((int)this.position.x,
                             (int)this.position.y,
                             (int)this.size.x,
                             (int)this.size.y);
    }
}
