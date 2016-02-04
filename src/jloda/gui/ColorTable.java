/*
 *  Copyright (C) 2015 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jloda.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

/**
 * a color table
 * Created by huson on 1/31/16.
 */
public class ColorTable {
    private final String name;
    private final Color[] colors;

    /**
     * constructor
     *
     * @param name
     * @param colors
     */
    public ColorTable(String name, Color... colors) {
        this.name = name;
        this.colors = colors;
    }

    /**
     * constructor
     *
     * @param name
     * @param colors
     */
    public ColorTable(String name, Collection<Color> colors) {
        this.name = name;
        this.colors = colors.toArray(new Color[colors.size()]);
    }

    /**
     * get name of color scheme
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * get color
     *
     * @param i (is used modulo number of colors)
     * @return color
     */
    public Color get(int i) {
        return colors[Math.abs(i) % colors.length];
    }

    /**
     * get the i-th color
     *
     * @param i
     * @param alpha
     * @return color
     */
    public Color get(int i, int alpha) {
        Color color = get(i);
        if (color.getRed() > 210 && color.getGreen() > 210 && color.getBlue() > 210)
            color = color.darker();

        if (color.getAlpha() == alpha)
            return color;
        else
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public Color[] getColors() {
        return colors;
    }

    public int size() {
        return colors.length;
    }

    /**
     * gets the command needed to undo this command
     *
     * @return undo command
     */
    public String getUndo() {
        return null;
    }

    /**
     * make an icon for this color table
     *
     * @return icon
     */
    public ImageIcon makeIcon() {
        final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);

        int patchSize = 16 / (int) (1 + Math.sqrt(size()));

        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                image.setRGB(x, y, Color.LIGHT_GRAY.getRGB());

        int row = 0;
        int col = 0;
        for (Color color : getColors()) {
            for (int y = 0; y < patchSize; y++) {

                if (row + patchSize >= 16) {
                    row = 0;
                    col += patchSize;
                }

                for (int x = 0; x < patchSize; x++) {
                    if (row + x < 16 && col + y < 16)
                        image.setRGB(row + x, col + y, color.getRGB());
                }
            }
            row += patchSize;
        }
        return new ImageIcon(image);
    }
}