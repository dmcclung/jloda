/**
 * EdgeIntegerArray.java 
 * Copyright (C) 2019 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * @version $Id: EdgeIntegerArray.java,v 1.6 2005-12-05 13:25:44 huson Exp $
 *
 * @author Daniel Huson
 *
 */
package jloda.graph;


/**
 * Edge int array
 * Daniel Huson, 2003
 */

public class EdgeIntegerArray extends EdgeArray<Integer> {
    /**
     * Construct an edge int array for the given graph and initialize all
     * entries to value.
     *
     * @param g   Graph
     * @param val int
     */
    public EdgeIntegerArray(Graph g, int val) {
        super(g, val);
    }

    /**
     * Construct an edge int array.
     *
     * @param g Graph
     */
    public EdgeIntegerArray(Graph g) {
        super(g);
    }

    /**
     * Construct an edge int map.
     *
     * @param src
     */
    public EdgeIntegerArray(EdgeIntegerMap src) {
        super(src);
    }

    /**
     * Construct an edge int map.
     *
     * @param src
     */
    public EdgeIntegerArray(EdgeIntegerArray src) {
        super(src);
    }

    /**
     * Get the entry for edge e.
     *
     * @param e Edge
     * @return an integer value the entry for edge e
     */
    public int getValue(Edge e) {
        if (super.get(e) == null)
            return 0;
        else
            return super.get(e);
    }

    /**
     * Set the entry for edge e to obj.
     *
     * @param e     Edge
     * @param value int
     */
    public void set(Edge e, int value) {
        super.set(e, value);
    }

    /**
     * Set the entry for all edges.
     *
     * @param val int
     */
    public void setAll(int val) {
        super.setAll(val);
    }
}

// EOF
