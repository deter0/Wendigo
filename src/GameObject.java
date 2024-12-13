import java.awt.*;

public abstract class GameObject {
    public boolean isDrawn = true;
    public boolean isUpdated = true;

    public abstract void Draw(Graphics2D g);
    public abstract void Update(double deltaTime);
}
