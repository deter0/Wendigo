import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.ArrayList;

public class Physics {
    public TileMap currentMap = null;
    public ArrayList<GameObject> physicsObjects = new ArrayList<>();

    private ArrayList<String> collisionRules = new ArrayList<>(); 

    public void SetCollidable(String layerOne, String layerTwo, boolean collidable) {
        String rule = layerOne + "|" + layerTwo;
        if (collidable == true) {
            this.collisionRules.remove(rule);
        } else if (!this.collisionRules.contains(rule)) {
            this.collisionRules.add(rule);
        }
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
        if (o.velocity.magnitude() > 0.01) {
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

                boolean skip = false;
                if (o.collisionLayers != null && otherO.collisionLayers != null) {
                    for (String clayer : o.collisionLayers) {
                        for (String colayer : otherO.collisionLayers) {
                            String ruleString = clayer + "|" + colayer;
                            if (this.collisionRules.contains(ruleString)) {
                                skip = true;
                                break;
                            }
                        }
                        if (skip) break;
                    }
                }
                if (skip) continue;

                Rectangle otherRect = otherO.GetRect();
                CheckCollision(rect, otherRect, o, otherO);
            }

            // Check for collisions with static objects
            for (Rectangle staticCollidor : mapStaticCollidors) {
                CheckCollision(rect, staticCollidor, o, null);
            }
        }
    }

    public ArrayList<Vector2> getLineRectangleIntersection(Vector2 p1, Vector2 p2, Rectangle rect) {
        ArrayList<Vector2> intersections = new ArrayList<>();
    
        // Rectangle edges represented as lines
        Line2D.Double top = new Line2D.Double(rect.x, rect.y, rect.x + rect.width, rect.y);
        Line2D.Double bottom = new Line2D.Double(rect.x, rect.y + rect.height, rect.x + rect.width, rect.y + rect.height);
        Line2D.Double left = new Line2D.Double(rect.x, rect.y, rect.x, rect.y + rect.height);
        Line2D.Double right = new Line2D.Double(rect.x + rect.width, rect.y, rect.x + rect.width, rect.y + rect.height);
    
        // The input line
        Line2D.Double line = new Line2D.Double(p1.x, p1.y, p2.x, p2.y);
    
        // Check each edge of the rectangle for intersection
        checkLineIntersection(line, top, intersections);
        checkLineIntersection(line, bottom, intersections);
        checkLineIntersection(line, left, intersections);
        checkLineIntersection(line, right, intersections);

        return intersections;
    }
    
    private void checkLineIntersection(Line2D.Double line1, Line2D.Double line2, ArrayList<Vector2> intersections) {
        // Get intersection point if it exists
        Vector2 intersection = getIntersectionPoint(line1, line2);
        if (intersection != null) {
            intersections.add(intersection);
        }
    }
    
    private Vector2 getIntersectionPoint(Line2D.Double line1, Line2D.Double line2) {
        double x1 = line1.x1, y1 = line1.y1, x2 = line1.x2, y2 = line1.y2;
        double x3 = line2.x1, y3 = line2.y1, x4 = line2.x2, y4 = line2.y2;
    
        // Compute the determinants
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denom == 0.0) return null; // Lines are parallel or coincident
    
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;
    
        // Check if intersection is within the bounds of both lines
        if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
            double ix = x1 + t * (x2 - x1);
            double iy = y1 + t * (y2 - y1);
            return new Vector2(ix, iy);
        }
    
        return null; // No valid intersection
    }
    
    class RaycastResult  {
        Vector2 position = null;
        Vector2 normal = new Vector2();
        double distance = Double.MAX_VALUE;
        Object hit = null;
    }

    public RaycastResult RayCast(Vector2 position, Vector2 direction, String[] ignoreCollisionLayers) {
        RaycastResult closestResult = new RaycastResult();

        for (Rectangle or : mapStaticCollidors) {
            ArrayList<Vector2> intersections = getLineRectangleIntersection(position, position.add(direction), or);

            for (Vector2 intersection : intersections) {
                double distance = intersection.distance(position);
                if (distance < closestResult.distance) {
                    closestResult.hit = null;
                    closestResult.position = intersection;
                    closestResult.distance = distance;
                    closestResult.hit = null;
                }
            }
        }

        for (GameObject o : this.physicsObjects) {
            boolean skip = false;

            if (o.collisionLayers != null && ignoreCollisionLayers != null) {
                for (String ignore : ignoreCollisionLayers) {
                    if (o.collisionLayers.contains(ignore)) {
                        skip = true;
                        break;
                    }
                }
            }

            if (skip) continue;

            Rectangle or = o.GetRect();

            ArrayList<Vector2> intersections = getLineRectangleIntersection(position, position.add(direction), or);

            for (Vector2 intersection : intersections) {
                double distance = intersection.distance(position);
                if (distance < closestResult.distance) {
                    closestResult.hit = null;
                    closestResult.position = intersection;
                    closestResult.distance = distance;
                    closestResult.hit = o;
                }
            }
        }

        if (closestResult.position != null)
            return closestResult;

        return null;
    }

    public RaycastResult RayCast(Vector2 position, Vector2 direction) {
        return RayCast(position, direction, null);
    }


    public void PreUpdate() {
        this.physicsObjects.clear();
    }

    public void Draw(Graphics2D g) {
        // for (GameObject o : this.physicsObjects) {
        //     o.DrawOutline(g);
        // }

        // for (Rectangle r : mapStaticCollidors) {
        //     g.setColor(Color.RED);
        //     GG.drawRect(r);
        // }

        RaycastResult r = RayCast(Game.player.position, Game.worldMousePos.sub(Game.player.position), new String[]{"player"});
        if (r != null) {
            g.setColor(Color.RED);
            GG.drawOval(r.position, new Vector2(5));
        }
    }
}
