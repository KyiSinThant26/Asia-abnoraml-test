/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package dk.dma.ais.abnormal.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CategorizerTest {

    @Test
    public void testMapCourseOverGroundToCategory() {
        assertEquals(1, Categorizer.mapCourseOverGroundToCategory((float) 0.0));
        assertEquals(1, Categorizer.mapCourseOverGroundToCategory((float) 29.999));
        assertEquals(2, Categorizer.mapCourseOverGroundToCategory((float) 30.0));
        assertEquals(12, Categorizer.mapCourseOverGroundToCategory((float) 359.999));
        assertEquals(1, Categorizer.mapCourseOverGroundToCategory((float) 360.000));
    }

    @Test
    public void testMapShipTypeCategoryToString() {
        assertEquals("tanker", Categorizer.mapShipTypeCategoryToString((short) 1));
        assertEquals("cargo", Categorizer.mapShipTypeCategoryToString((short) 2));
    }

    @Test
    public void testMapShipSizeCategoryToString() {
        assertEquals("undef", Categorizer.mapShipSizeCategoryToString((short) 1));
    }

    @Test
    public void testMapCourseOverGroundCategoryToString() {
        assertEquals("000-030", Categorizer.mapCourseOverGroundCategoryToString((short) 1));
    }

    @Test
    public void testMapSpeedOverGroundCategoryToString() {
        assertEquals("1-5kts", Categorizer.mapSpeedOverGroundCategoryToString((short) 2));
    }
}
