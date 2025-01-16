import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;

public class Physics {
    public TileMap currentMap = null;
    public ArrayList<GameObject> physicsObjects = new ArrayList<>();

    private void resolveCollision(GameObject o1, GameObject o2, Vector2 normal, double penetrationDepth) {
        double percent = 1.0; // Positional correction scalar
        Vector2 correction = normal.scale(penetrationDepth * percent);
        
        double totalMass = o1.mass + o2.mass;
        o1.position = o1.position.sub(correction.scale(o1.mass / totalMass));
        o2.position = o2.position.add(correction.scale(o2.mass / totalMass));
    
        // Velocity Resolution
        Vector2 relativeVelocity = o2.velocity.sub(o1.velocity);
        double velocityAlongNormal = relativeVelocity.dot(normal);
        
        // Do not resolve if velocities are separating
        if (velocityAlongNormal > 0) return;
    
        double restitution = 0.8; // Coefficient of restitution (adjust for bounciness)
        double impulseMagnitude = -(1 + restitution) * velocityAlongNormal / (1 / o1.mass + 1 / o2.mass);
        
        Vector2 impulse = normal.scale(impulseMagnitude);
        o1.velocity = o1.velocity.sub(impulse.scale(1 / o1.mass));
        o2.velocity = o2.velocity.add(impulse.scale(1 / o2.mass));
    }

    public void CheckCollision(Rectangle rect, Rectangle otherRect, GameObject o, GameObject otherO) {
        if (otherRect.intersects(rect)) {
            // Calculate intersection depths
            double overlapX = Math.min(rect.getMaxX(), otherRect.getMaxX()) - Math.max(rect.getMinX(), otherRect.getMinX());
            double overlapY = Math.min(rect.getMaxY(), otherRect.getMaxY()) - Math.max(rect.getMinY(), otherRect.getMinY());

            double penetrationDepth = 0;
            // Determine the axis of minimum penetration
            Vector2 collisionNormal = new Vector2(0, 0);
            if (overlapX < overlapY) {
                // Collision is along the x-axis
                collisionNormal.x = rect.getCenterX() < otherRect.getCenterX() ? -1 : 1;
                penetrationDepth = overlapX;
            } else {
                // Collision is along the y-axis
                collisionNormal.y = rect.getCenterY() < otherRect.getCenterY() ? -1 : 1;
                penetrationDepth = overlapY;
            }

            
            if (penetrationDepth > 0.05) {
                o.position.x += overlapX * 0.9 * collisionNormal.x;
                o.position.y += overlapY * 0.9 * collisionNormal.y;
            }
        }
    }

    private ArrayList<Rectangle> mapStaticCollidors = new ArrayList<>();

    public void Update(double dt) {
        mapStaticCollidors = new ArrayList<>();

        if (this.currentMap != null) {
            for (TileMapLayer l : this.currentMap.layers) {
                for (Tile t : l.tiles) {
                    if (t.collidable == true) {
                        Vector2 tilePosition = currentMap.LocalToWorldVectorPositional(new Vector2(t.x, t.y));
                        Vector2 tileSize = currentMap.LocalToWorldVectorScalar(new Vector2(t.w, t.h));
                        
                        Rectangle collisionRect = new Rectangle();
                        
                        collisionRect.x = (int)(tilePosition.x + tileSize.x*t.collidorPos.x);
                        collisionRect.y = (int)(tilePosition.y + tileSize.y*t.collidorPos.y);
                        collisionRect.width = (int)(tileSize.x*t.collidorSize.x);
                        collisionRect.height = (int)(tileSize.y*t.collidorSize.y);

                        mapStaticCollidors.add(collisionRect);
                    }
                }
            }
        }

        for (GameObject o : this.physicsObjects) {
            Rectangle rect = o.GetRect();
            o.position = o.position.add(o.velocity.scale(dt));

            for (GameObject otherO : this.physicsObjects) {
                if (otherO == o) continue;
                Rectangle otherRect = otherO.GetRect();

                CheckCollision(rect, otherRect, o, otherO);
            }

            for (Rectangle staticCollidor : mapStaticCollidors) {
                CheckCollision(rect, staticCollidor, o, null);
            }
        }
    }
    
    public void Draw(Graphics2D g) {
        for (GameObject o : this.physicsObjects) {
            o.DrawOutline(g);
        }

        for (Rectangle r : mapStaticCollidors) {
            g.setColor(Color.RED);
            GG.drawRect(r);
        }

        this.physicsObjects.clear();
    }
}
