import java.awt.*;
import java.awt.event.*;

class TileMap extends GameObject {
    public String name;

    public TileMap(String name) {
        this.name = name;
    }

    public void Update(double dt) {

    }
    public void Draw(Graphics2D g) {

    }
}

enum PanelLayoutDir {
    HORIZONTAL,
    VERTICAL,
    NONE
}

enum PanelLayoutSpacing {
    SPACE_EVENLY,
    FIXED_SPACE
}

class Panel {
    public static Panel root;

    protected Panel[] children = new Panel[8];
    protected Panel parent;

    public Color background = GG.COLOR_OPAQUE;
    public Color foreground = Color.WHITE;

    public Vector2 position;
    public Vector2 size;

    protected Vector2 computedPosition;
    protected Vector2 computedSize;

    public double fixedSpacing;

    public PanelLayoutDir layoutDir;
    public PanelLayoutSpacing layoutSpacing;

    public Panel() {
        for (int i = 0; i < this.children.length; i++) {
            this.children[i] = null;
        }
    }

    public void ComputeSize() {
        this.computedSize = this.size;
    }
    
    public void ComputeLayout() {
        double x = this.computedPosition.x;
        for (int i = 0; i < this.children.length; i++) {
            Panel child = this.children[i];
            if (child != null) {
                child.ComputeLayout();
            }


        }
    }

    public void Draw(Graphics g) {
        g.setColor(this.background);
        GG.drawRect(this.position.x, this.position.y, this.size.x, this.size.y);

        for (int i = 0; i < this.children.length; i++) {
            if (this.children[i] != null) {
                this.children[i].Draw(g);
            }
        }
    }

    public void SetParent(Panel parent) {
        if (parent == null) {
            if (this.parent != null) {
                for (int i = 0; i < this.parent.children.length; i++) {
                    if (this.parent.children[i] == this) {
                        this.parent.children[i] = null;
                    }
                }
                this.parent = null;
            }
            return;
        }

        boolean insertedSelf = false;
        for (int i = 0; i < parent.children.length; i++) {
            if (parent.children[i] == null) {
                parent.children[i] = this;
                insertedSelf = true;
                break;
            }
        }

        if (!insertedSelf) {
            System.out.println("[ERROR]: Error setting parent for panel. Parent has no more space for children.\n");
        } else {
            this.parent = parent;
        }
    }

    public static void SetRoot(Panel root) {
        Panel.root = root;
    }

    public static Panel BeginLayout(PanelLayoutDir dir, PanelLayoutSpacing spacing, double fixedSpacing) {
        Panel layout = new Panel();
        layout.layoutDir = dir;
        layout.layoutSpacing = spacing;
        layout.fixedSpacing = fixedSpacing;
        layout.SetParent(Panel.root);
        Panel.root = layout;
        return layout;
    }

    public static void End() {
        if (Panel.root.parent == null) {
            System.out.println("[ERROR]: Error ending panel layout. Already at top.\n");
        } else {
            Panel.root = Panel.root.parent;
        }
    }

    public static void Finish() {
        Panel.root.ComputeLayout();
    }
}

public class TileMapEditor extends GameObject {
    protected TileMap currentMap; // Currently editing map

    public void EditMap(TileMap map) {
        this.currentMap = map;
    }

    public void Update(double dt) {

    }

    private int bottomPanelHeight = 200;
    private boolean draggingPanel = false;

    private void DragPanel(Rectangle panel) {
        double deltaMouseY = Math.abs(Game.mousePos.y - panel.y);
        if (draggingPanel || deltaMouseY < 20) {
            Game.currentCursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);

            if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
                this.draggingPanel = true;
            } else if (Game.IsMouseReleased(MouseEvent.BUTTON1)) {
                this.draggingPanel = false;
            }
        }
        if (this.draggingPanel) {
            this.bottomPanelHeight = (int)(Game.HEIGHT - Game.mousePos.y);
        }
    }

    public void Draw(Graphics2D g) {
        Rectangle bottomPanel = new Rectangle(0, Game.HEIGHT - bottomPanelHeight, Game.WIDTH, bottomPanelHeight);
        
        DragPanel(bottomPanel);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(bottomPanel.x, bottomPanel.y, bottomPanel.width, bottomPanel.height);
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(bottomPanel.x, bottomPanel.y, bottomPanel.width - 1, bottomPanel.height - 1);

        double y = bottomPanel.y + 20.0, x = bottomPanel.x + 4.0;

        g.setFont(Game.font16);
        GG.drawString("Map: " + this.currentMap.name, x, y);
        y += Game.font16.getSize();
        // GG.drawString("Hello", x, y);
    }
}
