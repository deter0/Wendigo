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
    public static Panel root = null;

    protected Panel[] children = new Panel[8];
    protected Panel parent = null;

    public Color background = Color.DARK_GRAY;
    public Color foreground = Color.WHITE;

    public boolean positionAbsolute = false;
    public Vector2 position = new Vector2();
    public Vector2 size = new Vector2();

    protected Vector2 computedPosition = new Vector2();
    protected Vector2 computedSize = new Vector2();

    public double fixedSpacing = 0.0;

    public PanelLayoutDir layoutDir = PanelLayoutDir.NONE;
    public PanelLayoutSpacing layoutSpacing = PanelLayoutSpacing.FIXED_SPACE;


    public Panel() {
        for (int i = 0; i < this.children.length; i++) {
            this.children[i] = null;
        }
    }

    public void ComputeSize() {
        double pWidth = Game.WIDTH, pHeight = Game.HEIGHT;
        if (this.parent != null && this.parent.computedSize != null) {
            pWidth = this.parent.computedSize.x;
            pHeight = this.parent.computedSize.y;
        }

        this.computedSize = new Vector2(this.size.x <= 1.0 ? this.size.x * pWidth : this.size.x,
                                        this.size.y <= 1.0 ? this.size.y * pHeight : this.size.y);
    }
    
    public void ComputeLayout() {
        this.ComputeSize();

        double pX = 0, pY = 0;
        if (this.parent != null && this.parent.computedSize != null) {
            pX  = this.parent.computedPosition.x;
            pY  = this.parent.computedPosition.y;
        }

        this.computedPosition = this.computedPosition.add(new Vector2(pX, pY));

        if (this.positionAbsolute) {
            this.computedPosition = this.computedPosition.add(this.position);
        }

        if (this.layoutDir == PanelLayoutDir.NONE) {
            for (int i = 0; i < this.children.length; i++) {
                Panel child = this.children[i];
                if (child != null) {
                    child.computedPosition = child.position;
                    child.ComputeLayout();
                }
            }
        } else if (this.layoutDir == PanelLayoutDir.HORIZONTAL) {
            if (this.layoutSpacing == PanelLayoutSpacing.FIXED_SPACE) {
                Vector2 childPosition = new Vector2(0, 0);
                for (int i = 0; i < this.children.length; i++) {
                    Panel child = this.children[i];
                    if (child != null) {
                        child.ComputeSize();
                        child.computedPosition = child.computedPosition.add(childPosition);
                        childPosition = childPosition.add(new Vector2(child.computedSize.x + this.fixedSpacing, 0.0));
                        System.out.println(i + ": " + childPosition);
                        child.ComputeLayout();
                    }
                }
            } else if (this.layoutSpacing == PanelLayoutSpacing.SPACE_EVENLY) {
                double spacingLeft = this.computedSize.x, spacingPerChild;
                int numChildren = 0;
                for (int i = 0; i < this.children.length; i++) {
                    Panel child = this.children[i];
                    if (child != null) {
                        numChildren++;
                        child.ComputeSize();
                        spacingLeft -= child.computedSize.x;
                    }
                }
                spacingPerChild = spacingLeft/(double)numChildren;
                // if (child != null) {
                //     child.ComputeSize();
                //     child.computedPosition = child.computedPosition.add(childPosition);
                //     childPosition = childPosition.add(new Vector2(child.computedSize.x + this.fixedSpacing, 0.0));
                //     System.out.println(i + ": " + childPosition);
                //     child.ComputeLayout();
                // }
            }
        }
    }

    public void Draw(Graphics g) {
        g.setColor(this.background);
        GG.fillRect(this.computedPosition.x, this.computedPosition.y, this.computedSize.x, this.computedSize.y);

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
        layout.size = new Vector2(1.0, 1.0);
        layout.position = new Vector2();
        layout.background = GG.COLOR_OPAQUE;
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

    public static void Finish(Graphics g) {
        Panel.root.ComputeLayout();
        Panel.root.Draw(g);
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

        Panel n = new Panel();
        n.position = new Vector2(bottomPanel.x, bottomPanel.y);
        n.size = new Vector2(1.0, bottomPanel.height);
        n.positionAbsolute = true;
        Panel.SetRoot(n);

        Panel p = Panel.BeginLayout(PanelLayoutDir.HORIZONTAL, PanelLayoutSpacing.FIXED_SPACE, 20.0);
        p.background = Color.RED;
        for (int i = 0; i < 3; i++) {
            p = Panel.BeginLayout(PanelLayoutDir.NONE, PanelLayoutSpacing.FIXED_SPACE, 0.0);
            p.size = new Vector2(100, 100);
            p.background = Color.YELLOW;
            Panel.End();
        }
        Panel.End();
        
        Panel.Finish(g);

        // g.setColor(Color.DARK_GRAY);
        // g.fillRect(bottomPanel.x, bottomPanel.y, bottomPanel.width, bottomPanel.height);
        // g.setColor(Color.LIGHT_GRAY);
        // g.drawRect(bottomPanel.x, bottomPanel.y, bottomPanel.width - 1, bottomPanel.height - 1);

        // double y = bottomPanel.y + 20.0, x = bottomPanel.x + 4.0;

        // g.setFont(Game.font16);
        // GG.drawString("Map: " + this.currentMap.name, x, y);
        // y += Game.font16.getSize();
        // GG.drawString("Hello", x, y);
    }
}
