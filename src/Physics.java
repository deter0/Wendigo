import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;

public class Physics {
    public TileMap currentMap = null;
    public ArrayList<GameObject> physicsObjects = new ArrayList<>();

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

            // Relative velocity
            Vector2 relativeVelocity = otherO != null 
                                            ? o.velocity.sub(otherO.velocity) 
                                            : o.velocity;

            // Velocity along the normal
            double velocityAlongNormal = relativeVelocity.dot(collisionNormal);

            // Skip if velocities are separating
            if (velocityAlongNormal > 0) {
                return;
            }

            // Coefficient of restitution (elasticity)
            double restitution = otherO != null ? Math.min(o.restitution, otherO.restitution) : o.restitution;

            // Calculate impulse scalar
            double impulseMagnitude = -(1 + restitution) * velocityAlongNormal;
            impulseMagnitude /= otherO != null ? (1 / o.mass + 1 / otherO.mass) : (1 / o.mass);

            // Impulse vector
            Vector2 impulse = collisionNormal.scale(impulseMagnitude);

            // Apply impulse to moving object
            o.velocity = o.velocity.add(impulse.scale(1 / o.mass));

            // Apply impulse to other object if it's not null and movable
            if (otherO != null) {
                otherO.velocity = otherO.velocity.sub(impulse.scale(1 / otherO.mass));
            }
        }
    }

    public void ApplyFriction(GameObject o, double dt) {
        // Assuming the friction coefficient is stored in the object
        double frictionCoefficient = o.frictionCoefficient; // e.g., 0.1 for a rough surface
        double mass = o.mass;
        
        // If the object is moving, apply kinetic friction
        if (o.velocity.magnitude() > 0) {
            // // Frictional acceleration
            double frictionAcceleration = o.velocity.magnitude() * frictionCoefficient;
            
            // // Apply friction in the opposite direction of velocity
            Vector2 frictionDirection = o.velocity.normalize().scale(-1); // Opposite direction
            Vector2 frictionEffect = frictionDirection.scale(frictionAcceleration);
            
            // // Reduce velocity by the friction effect
            o.velocity = o.velocity.add(frictionEffect);
    
            // // Ensure that the velocity doesn't become negative (slowing down too much)
            if (o.velocity.magnitude() < 0.1) {
                o.velocity = new Vector2(0, 0); // Object is considered stopped
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

            // Apply friction to the object
            ApplyFriction(o, dt);

            // Check for collisions with other objects
            for (GameObject otherO : this.physicsObjects) {
                if (otherO == o) continue;
                Rectangle otherRect = otherO.GetRect();
                CheckCollision(rect, otherRect, o, otherO);
            }

            // Check for collisions with static objects
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
