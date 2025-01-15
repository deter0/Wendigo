import java.awt.*;

public abstract class GameObject {
    public Vector2 position = new Vector2();
    public Vector2 size = new Vector2();
    public boolean isDrawn = true;
    public boolean isUpdated = true;

    public abstract void Draw(Graphics2D g);
    public abstract void Update(double deltaTime);
    
}
