/* GG.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
* Description: GG (Good graphics), implement graphic methods but 
* using generic number types instead of ints.
*/

import java.awt.*;

public class GG {
    public static Graphics2D g;
    public static Color COLOR_OPAQUE = new Color(0, 0, 0, 0);

    public static <N extends Number> void drawOval(N x, N y, N w, N h) {
        g.drawOval(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void fillOval(N x, N y, N w, N h) {
        g.fillOval(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void drawString(String text, N x, N y) {
        g.drawString(text, x.intValue(), y.intValue());
    }

    public static <N extends Number> void fillRect(N x, N y, N w, N h) {
        g.fillRect(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public static <N extends Number> void drawRect(N x, N y, N w, N h) {
        g.drawRect(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    // TODO: Implement more functions
}
