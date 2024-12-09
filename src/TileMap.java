import java.util.ArrayList;

import com.raylib.java.Raylib.*;
import com.raylib.java.core.Color;
import com.raylib.java.core.rcamera.Camera2D;
import com.raylib.java.raymath.Matrix;
import com.raylib.java.raymath.Vector2;
import com.raylib.java.shapes.Rectangle;
import com.raylib.java.textures.*;
import com.raylib.java.rlgl.*;

import static com.raylib.java.raymath.Raymath.*;


// The main tile class
class Tile {
    // Which atlas to sample
    protected Atlas atlas = null;
    
    // Position in atlas
    public int atlasX = 0;
    public int atlasY = 0;
    
    public Tile(int x, int y, Atlas atlas) {
        this.atlasX = x;
        this.atlasY = y;
        this.atlas = atlas;
    }
    public Tile() { }
}

// Class that holds the atlases (textures)
class Atlas {
    public int tileSize; // Each tiles size
    public Texture2D texture; // The texture
    
    protected int width;
    protected int height;
    
    // Constructor, The file path and the tile size are provided
    public Atlas(String imagePath, int tileSize) {
        // Set properties
        this.tileSize = tileSize;
        
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        System.out.println("[INFO]: Loading texture atlas texture from disk: " + imagePath);

        Image img = Wend.rtextures.LoadImage(imagePath);
        
        // if (this.texture.id <= 0) {
        //     System.err.println("[ERROR]: Loading atlas: " + imagePath);
        // }
        
        // this.width = this.texture.width / tileSize;
        // this.height = this.texture.height / tileSize;
        
        System.out.println("Width: " + img.width);
    }
}

public class TileMap {
    protected int width, height; // Width and height of the entire map
    
    protected ArrayList<Atlas> atlases = new ArrayList<>(4); // All the texture atlases we use
    protected ArrayList<Tile> tiles = new ArrayList<>(); // The tiles
    
    // How much we scale up this map in rendedring
    public Vector2 renderOffset = new Vector2();
    public float scale = 100.0f;
    
    // Add to list of all atlases this map uses
    public void SupplyAtlas(Atlas atlas) {
        this.atlases.add(atlas);
    }
    
    // Constructor function with width and height
    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;    
        this.tiles = new ArrayList<Tile>(width * height); // Create all the tiles
        
        for (int i = 0; i < width * height; i++) {
            this.tiles.add(null); // Add empty tiles
        }
    }
    
    public void SetTile(int x, int y, Atlas atlas, int spriteIndex) {
        int index = x * this.width + y;
        Tile t = this.tiles.get(index);

        if (t == null) {
            t = new Tile();
        }
        t.atlasX = spriteIndex / atlas.width;
        t.atlasY = spriteIndex % atlas.height;
        t.atlas = atlas;

        this.tiles.set(index, t);
    }
    
    public void Render(Camera2D camera) {
        Matrix cameraMatrix = Wend.rl.core.GetCameraMatrix2D(camera);

        Vector2 mousePositionScreen = Wend.rl.core.GetMousePosition();
        Vector2 mousePositionRelative = Vector2Transform(mousePositionScreen, MatrixInvert(cameraMatrix));

        Wend.rl.shapes.DrawCircle((int)mousePositionRelative.x, (int)mousePositionRelative.y, 1.f, Color.RED);
        
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int index = x * this.width + y;
                Tile t = this.tiles.get(index);
                if (y == 4 && x == 3) {
                    // System.out.println(t.x + "," +  t.y);
                }
                
                Vector2 screenPosition = new Vector2((this.renderOffset.x + x) * this.scale,
                                                     (this.renderOffset.y + y) * this.scale);
                Rectangle rect = new Rectangle(screenPosition, this.scale, this.scale);
                
                Wend.rl.shapes.DrawRectangleLinesEx(rect, 1.5f, Color.PURPLE);
                
                if (t != null) {
                    Rectangle source = new Rectangle(t.atlasX * t.atlas.tileSize, t.atlasY * t.atlas.tileSize,
                                                     t.atlas.tileSize, t.atlas.tileSize);
                    Rectangle dest = rect;
                    
                    Wend.rtextures.DrawTexturePro(t.atlas.texture, source, dest, screenPosition, 0.0f, Color.WHITE);
                }
            }
        }
    }
}
