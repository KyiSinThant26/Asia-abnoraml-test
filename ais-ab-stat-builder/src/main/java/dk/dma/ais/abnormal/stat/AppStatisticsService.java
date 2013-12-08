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

package dk.dma.ais.abnormal.stat;

public interface AppStatisticsService {

    void start();

    void stop();

    void incMessageCount();

    void incPosMsgCount();

    void incStatMsgCount();

    void incFilteredPacketCount();

    void incUnfilteredPacketCount();

    long getFilteredPacketCount();

    long getUnfilteredPacketCount();

    long getMessageCount();

    long getPosMsgCount();

    long getStatMsgCount();

    void setTrackCount(int trackCount);

    double getMessageRate();

    void incFeatureStatistics(String featureName, String statisticsName);

    Long getFeatureStatistics(String featureName, String statisticsName);

    void setFeatureStatistics(String featureName, String statisticsName, Long statisticsValue);

    void dumpStatistics();

}
