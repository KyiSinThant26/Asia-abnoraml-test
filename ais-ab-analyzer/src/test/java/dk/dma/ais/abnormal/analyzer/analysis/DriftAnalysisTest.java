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

package dk.dma.ais.abnormal.analyzer.analysis;

import dk.dma.ais.abnormal.tracker.Track;
import dk.dma.enav.model.geometry.Position;
import org.junit.Test;

import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.MIN_HDG_COG_DEVIATION_DEGREES;
import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.OBSERVATION_PERIOD_MINUTES;
import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.SPEED_HIGH_MARK;
import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.SPEED_LOW_MARK;
import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.isSignificantDeviation;
import static dk.dma.ais.abnormal.analyzer.analysis.DriftAnalysis.isSustainedDrift;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DriftAnalysisTest {

    private final float minHdgCogDeviationDegrees = MIN_HDG_COG_DEVIATION_DEGREES;

    @Test
    public void testIsSignificantDeviation() {
        assertFalse(isSignificantDeviation(0.0f, 0.0f + minHdgCogDeviationDegrees - 1.0f));
        assertFalse(isSignificantDeviation(0.0f, 0.0f + minHdgCogDeviationDegrees));
        assertTrue(isSignificantDeviation(0.0f, 0.0f + minHdgCogDeviationDegrees + 1.0f));

        assertFalse(isSignificantDeviation(90.0f, 90.0f));
        assertFalse(isSignificantDeviation(90.0f, 90.0f + minHdgCogDeviationDegrees - 1.0f));
        assertFalse(isSignificantDeviation(90.0f, 90.0f + minHdgCogDeviationDegrees));
        assertTrue(isSignificantDeviation(90.0f, 90.0f + minHdgCogDeviationDegrees + 1.0f));

        assertFalse(isSignificantDeviation(180.0f, 180.0f));
        assertFalse(isSignificantDeviation(180.0f, 180.0f + minHdgCogDeviationDegrees - 1.0f));
        assertFalse(isSignificantDeviation(180.0f, 180.0f + minHdgCogDeviationDegrees));
        assertTrue(isSignificantDeviation(180.0f, 180.0f + minHdgCogDeviationDegrees + 1.0f));

        assertFalse(isSignificantDeviation(270.0f, 270.0f));
        assertFalse(isSignificantDeviation(270.0f, 270.0f + minHdgCogDeviationDegrees - 1.0f));
        assertFalse(isSignificantDeviation(270.0f, 270.0f + minHdgCogDeviationDegrees));
        assertTrue(isSignificantDeviation(270.0f, 270.0f + minHdgCogDeviationDegrees + 1.0f));

        assertFalse(isSignificantDeviation(0.0f, 0.0f));
        assertFalse(isSignificantDeviation(0.0f, 359.9f));
        assertFalse(isSignificantDeviation(0.1f, 359.9f));
        assertFalse(isSignificantDeviation(0.0f + minHdgCogDeviationDegrees/2.0f, 0.0f - minHdgCogDeviationDegrees/2.0f));
        assertFalse(isSignificantDeviation(0.0f + minHdgCogDeviationDegrees/2.0f - 1, 0.0f - minHdgCogDeviationDegrees/2.0f));
        assertTrue(isSignificantDeviation(0.0f + minHdgCogDeviationDegrees/2.0f, 0.0f - minHdgCogDeviationDegrees/2.0f - 1));
    }

    @Test
    public void testIsSustainedDrift() throws Exception {
        final long t0 = System.currentTimeMillis();

        final long t1 = t0 + OBSERVATION_PERIOD_MINUTES*60*1000;
        final long dt = 7000;

        // nonDriftingTrack (speed normal, cog/hdg deviation normal)
        System.out.println("Testing normally moving, non-drifting track");
        Track track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), 45.0f, 12.0f, 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        // slowButNonDriftingTrack (speed indicates drift, cog/hdg deviation does not)
        System.out.println("Testing track moving in observed speed interval, but not drifting");
        track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), 45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        // slowButNonDriftingTrack (speed does not indicate drift, cog/hdg deviation does)
        System.out.println("Testing track moving in normal speed, but cog/hdg deviation indicates drift");
        track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), -45.0f, (float) (SPEED_HIGH_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        // verySlowButNonDriftingTrack (neither speed nor cog/hdg deviation indicate drift)
        System.out.println("Testing track moving below observed speed interval and not drifting");
        track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), 45.0f, (float) (SPEED_LOW_MARK - 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        // driftingTrackButNotForLongEnough
        System.out.println("Testing track drifting, but not drifting for long enough");
        track = new Track(219000001);
        for (long t = t0; t < t1-60000; t += dt) {    // Non-drifting
            track.update(t, Position.create(56.0, 12.0), 45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        for (long t = t1-60000+1000; t < t1+60000; t += dt) { // Drifting
            track.update(t, Position.create(56.0, 12.0), -45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        // driftingTrackGettingOutOfDrift
        System.out.println("Testing track drifting, but eventually getting out of drift");
        track = new Track(219000001);
        for (long t = t0; t < t1-60000; t += dt) {    // Drifting
            track.update(t, Position.create(56.0, 12.0), -45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        for (long t = t1-60000+1000; t < t1+60000; t += dt) { // Non-drifting
            track.update(t, Position.create(56.0, 12.0), 45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertFalse(isSustainedDrift(track));

        //
        System.out.println("Testing drifting track 1");
        track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), -45.0f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertTrue(isSustainedDrift(track));

        System.out.println("Testing drifting track 2");
        track = new Track(219000001);
        for (long t = t0; t < t1+60000; t += dt) {
            track.update(t, Position.create(56.0, 12.0), 353.3f, (float) (SPEED_LOW_MARK + 0.5), 45.1f);
            //System.out.println("t:" + t + " pos:" + track.getPosition() + " sog:" + track.getSpeedOverGround() + " cog:" + track.getCourseOverGround() + " hdg:" + track.getTrueHeading());
        }
        assertTrue(isSustainedDrift(track));

    }

}