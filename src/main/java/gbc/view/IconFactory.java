package gbc.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.swing.FontIcon;

final class IconFactory {
    private IconFactory() {
    }

    static Icon open() {
        return icon(MaterialDesignF.FOLDER_OPEN, 16);
    }

    static Icon recent() {
        return icon(MaterialDesignH.HISTORY, 16);
    }

    static Icon save() {
        return icon(MaterialDesignC.CONTENT_SAVE, 16);
    }

    static Icon load() {
        return icon(MaterialDesignF.FOLDER_DOWNLOAD, 16);
    }

    static Icon screenshot() {
        return icon(MaterialDesignC.CAMERA, 16);
    }

    static Icon exit() {
        return icon(MaterialDesignL.LOGOUT, 16);
    }

    static Icon pause() {
        return icon(MaterialDesignP.PAUSE, 16);
    }

    static Icon play() {
        return icon(MaterialDesignP.PLAY, 16);
    }

    static Icon reset() {
        return icon(MaterialDesignR.RESTART, 16);
    }

    static Icon apply() {
        return icon(MaterialDesignC.CHECK, 16);
    }

    static Icon close() {
        return icon(MaterialDesignC.CLOSE, 16);
    }

    static Icon restore() {
        return icon(MaterialDesignR.RESTORE, 16);
    }

    static Icon step() {
        return icon(MaterialDesignS.STEP_FORWARD, 16);
    }

    static Icon target() {
        return icon(MaterialDesignT.TARGET, 16);
    }

    static Icon fullscreen() {
        return icon(MaterialDesignF.FULLSCREEN, 16);
    }

    static Icon settings() {
        return icon(MaterialDesignC.COG, 16);
    }

    static Icon debug() {
        return icon(MaterialDesignB.BUG, 16);
    }

    static Icon help() {
        return icon(MaterialDesignH.HELP_CIRCLE_OUTLINE, 16);
    }

    private static Icon icon(Ikon glyph, int size) {
        FontIcon icon = FontIcon.of(glyph, size);
        return icon;
    }

    static Icon vramViewer() {
        return icon(MaterialDesignG.GRID, 16);
    }

    static Icon memoryDump() {
        return icon(MaterialDesignM.MEMORY, 16);
    }

    static Icon registerDump() {
        return icon(MaterialDesignI.INFORMATION_OUTLINE, 16);
    }

    static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Body: purple-gray rounded rect
        g.setColor(new Color(108, 100, 118));
        int bodyInset = size / 10;
        g.fillRoundRect(bodyInset, bodyInset, size - bodyInset * 2, size - bodyInset * 2,
                size / 4, size / 4);

        // Screen bezel
        int screenX = size * 22 / 100;
        int screenY = size * 16 / 100;
        int screenW = size * 56 / 100;
        int screenH = size * 40 / 100;
        g.setColor(new Color(40, 40, 48));
        g.fillRoundRect(screenX, screenY, screenW, screenH, size / 10, size / 10);

        // LCD green screen
        int lcdInset = size / 20;
        g.setColor(new Color(139, 172, 15));
        g.fillRect(screenX + lcdInset, screenY + lcdInset,
                screenW - lcdInset * 2, screenH - lcdInset * 2);

        // D-pad (cross)
        int dpadCx = size * 30 / 100;
        int dpadCy = size * 72 / 100;
        int arm = size * 7 / 100;
        int armLen = size * 10 / 100;
        g.setColor(new Color(50, 50, 55));
        g.fillRect(dpadCx - arm, dpadCy - armLen, arm * 2, armLen * 2);
        g.fillRect(dpadCx - armLen, dpadCy - arm, armLen * 2, arm * 2);

        // A and B buttons
        int btnRadius = size * 6 / 100;
        g.setColor(new Color(160, 50, 70));
        g.fillOval(size * 72 / 100 - btnRadius, size * 68 / 100 - btnRadius, btnRadius * 2, btnRadius * 2);
        g.fillOval(size * 60 / 100 - btnRadius, size * 76 / 100 - btnRadius, btnRadius * 2, btnRadius * 2);

        g.dispose();
        return img;
    }

    static List<BufferedImage> appIcons() {
        return List.of(appIcon(16), appIcon(32), appIcon(48), appIcon(64));
    }

    static Icon badge(Icon base, Color color) {
        int w = base.getIconWidth();
        int h = base.getIconHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        base.paintIcon(null, g2, 0, 0);
        g2.setColor(color);
        int r = Math.max(3, Math.min(w, h) / 5);
        g2.fillOval(w - r - 1, h - r - 1, r, r);
        g2.dispose();
        return new ImageIcon(img);
    }
}
