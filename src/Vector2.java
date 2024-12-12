/* Game.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
* Description: Vector2 utility
*/

class Vector2 {
    double x, y;
    
    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public Vector2() {
        this.x = 0.f;
        this.y = 0.f;
    }
    
    public Vector2(double s) {
        this.x = s;
        this.y = s;
    }
    public double magnitude() {
        return Math.sqrt(Math.pow(this.x, 2) + Math.pow(this.y, 2));
    }
    public Vector2 divide(Vector2 rhs) {
        return new Vector2(this.x / rhs.x, this.y / rhs.y);
    }
    public Vector2 mult(Vector2 rhs) {
        return new Vector2(this.x * rhs.x, this.y * rhs.y);
    }
    public Vector2 add(Vector2 rhs) {
        return new Vector2(this.x + rhs.x, this.y + rhs.y);
    }
    public double dot(Vector2 rhs) {
        return this.x*rhs.x + this.y*rhs.y;
    }
    public Vector2 sub(Vector2 rhs) {
        return new Vector2(this.x - rhs.x, this.y - rhs.y);
    }
    public Vector2 pow(double exp) {
        return new Vector2(Math.pow(this.x, exp), Math.pow(this.y, exp));
    }
    public Vector2 scale(double factor) {
        return new Vector2(this.x * factor, this.y * factor);
    }
    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }
    public double distance(Vector2 other) {
        return Math.sqrt(Math.pow(this.x - other.x, 2.0) + Math.pow(this.y - other.y, 2.0));
    }
    public Vector2 normalize() {
        return this.scale(1.0/this.magnitude());
    }
    public static double lerp(double a, double b, double t) {
        return a * (1.0 - t) + b * t;
    }
    public Vector2 lerp(Vector2 other, double t) {
        return new Vector2(lerp(this.x, other.x, t), lerp(this.y, other.y, t));
    }
    public Vector2 rotate(Vector2 k, double d) {
        // Translate the vector to the origin (relative to point k)
        double translatedX = this.x - k.x;
        double translatedY = this.y - k.y;

        // Perform rotation
        double rotatedX = translatedX * Math.cos(d) - translatedY * Math.sin(d);
        double rotatedY = translatedX * Math.sin(d) + translatedY * Math.cos(d);

        // Translate back to the original position
        return new Vector2(rotatedX + k.x, rotatedY + k.y);
    }
    public Vector2 rotate(double angle) {
        return this.rotate(new Vector2(), angle);
    }
}
