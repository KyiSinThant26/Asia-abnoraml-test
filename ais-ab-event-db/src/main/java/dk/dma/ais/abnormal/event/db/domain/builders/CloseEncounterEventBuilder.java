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

package dk.dma.ais.abnormal.event.db.domain.builders;

import dk.dma.ais.abnormal.event.db.domain.CloseEncounterEvent;

/**
 * This builder follows the expression builder pattern
 * http://martinfowler.com/bliki/ExpressionBuilder.html
 */
public class CloseEncounterEventBuilder extends EventBuilder {

    CloseEncounterEvent event;

    public CloseEncounterEventBuilder() {
        event = new CloseEncounterEvent();
    }

    public static CloseEncounterEventBuilder CloseEncounterEvent() {
        return new CloseEncounterEventBuilder();
    }

    public CloseEncounterEvent getEvent() {
        return event;
    }

    public ZoneBuilder safetyZoneOfPrimaryVessel() {
        ZoneBuilder builder = new ZoneBuilder(this);
        event.setSafetyZoneOfPrimaryVessel(builder.getZone());
        return builder;
    }

    public ZoneBuilder extentOfSecondaryVessel() {
        ZoneBuilder builder = new ZoneBuilder(this);
        event.setExtentOfSecondaryVessel(builder.getZone());
        return builder;
    }
}
