
import com.raylib.java.Raylib;
import com.raylib.java.core.Color;
import com.raylib.java.core.rcamera.Camera2D;
import com.raylib.java.raymath.Matrix;
import com.raylib.java.raymath.Vector2;
import com.raylib.java.textures.rTextures;

import static com.raylib.java.Config.ConfigFlag.*;
import static com.raylib.java.raymath.Raymath.*;
import static com.raylib.java.core.input.Keyboard.*;
import static com.raylib.java.utils.FileIO.*;

import java.io.IOException;

class Wend {
    public static Raylib rl = new Raylib();
    
    // To save from typing (e.g. Wend.rl.texture => Wend.rtextures)
    public static rTextures rtextures = rl.textures;
    
    public static void main(String[] args) throws IOException {
        rl.core.SetConfigFlags(FLAG_MSAA_4X_HINT | FLAG_VSYNC_HINT | FLAG_WINDOW_TOPMOST);
        rl.core.InitWindow(800, 600, "Wendigo");
        
        
        System.out.println("Working Directory = " + rl.core.GetWorkingDirectory());
        String txt = LoadFileText("C:\\Users\\kay\\Documents\\RaylibTest\\test.txt");
        System.out.println("TXT File: " + txt);
        
        if (true) {
            return;
        }
        
        Camera2D camera = new Camera2D(new Vector2(), new Vector2(0.5f, 0.5f), 0.0f, 1.0f);
        camera.setOffset(new Vector2(rl.core.GetScreenWidth()/2.f, rl.core.GetScreenHeight()/2.f));
        
        TileMap map1 = new TileMap(20, 20);
        Atlas atlas = new Atlas("C:/Users/kay/Documents/RaylibTest/res/Tile_set.png", 32);
        map1.SupplyAtlas(atlas);
        
        map1.SetTile(3, 4, atlas, 3);
        
        
        rl.core.SetTargetFPS(60);
        while (!rl.core.WindowShouldClose()) {
            float deltaTime = rl.core.GetFrameTime();
            
            Vector2 movementVector = new Vector2();
            if (rl.core.IsKeyDown(KEY_W)) {
                movementVector.y -= 1.0f;
            } else if (rl.core.IsKeyDown(KEY_S)) {
                movementVector.y += 1.0f;
            }
            if (rl.core.IsKeyDown(KEY_A)) {
                movementVector.x -= 1.0f;
            } else if (rl.core.IsKeyDown(KEY_D)) {
                movementVector.x += 1.0f;
            }
            movementVector = Vector2Normalize(movementVector);
            camera.setTarget(Vector2Add(camera.getTarget(), Vector2Scale(movementVector, 100.f * deltaTime)));
            
            rl.core.BeginDrawing();
            rl.core.ClearBackground(Color.BLACK);
            
            rl.core.BeginMode2D(camera);
            rl.shapes.DrawCircleV(new Vector2(100, 50), 10, Color.RED);
            
            if (rl.core.IsKeyDown(KEY_R)) {
                camera.zoom = camera.zoom + 1.f * deltaTime;
            } else if (rl.core.IsKeyDown(KEY_C)) {
                camera.zoom = camera.zoom - 1.f * deltaTime;
            }
            
            map1.Render(camera);
            
            rl.core.EndMode2D();
            rl.core.EndDrawing();
        }
    }
    
    // Convert a Matrix object to a float[] for rlMultMatrix
    public static float[] matrixToFloatArray(Matrix matrix) {
        return new float[]{
            matrix.m0, matrix.m4, matrix.m8,  matrix.m12,
            matrix.m1, matrix.m5, matrix.m9,  matrix.m13,
            matrix.m2, matrix.m6, matrix.m10, matrix.m14,
            matrix.m3, matrix.m7, matrix.m11, matrix.m15
        };
    }
}
