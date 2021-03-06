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

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import dk.dma.ais.abnormal.analyzer.AppStatisticsService;
import dk.dma.ais.abnormal.analyzer.behaviour.BehaviourManager;
import dk.dma.ais.abnormal.analyzer.behaviour.EventCertainty;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventLower;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventMaintain;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventRaise;
import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.ShipSizeOrTypeEvent;
import dk.dma.ais.abnormal.event.db.domain.TrackingPoint;
import dk.dma.ais.abnormal.stat.db.StatisticDataRepository;
import dk.dma.ais.abnormal.stat.db.data.ShipTypeAndSizeStatisticData;
import dk.dma.ais.abnormal.stat.db.data.StatisticData;
import dk.dma.ais.abnormal.util.Categorizer;
import dk.dma.ais.tracker.eventEmittingTracker.EventEmittingTracker;
import dk.dma.ais.tracker.eventEmittingTracker.InterpolatedTrackingReport;
import dk.dma.ais.tracker.eventEmittingTracker.Track;
import dk.dma.ais.tracker.eventEmittingTracker.events.CellChangedEvent;
import dk.dma.ais.tracker.eventEmittingTracker.events.TrackStaleEvent;
import dk.dma.enav.model.geometry.Position;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static dk.dma.ais.abnormal.analyzer.config.Configuration.CONFKEY_ANALYSIS_TYPESIZE_CELL_SHIPCOUNT_MIN;
import static dk.dma.ais.abnormal.analyzer.config.Configuration.CONFKEY_ANALYSIS_TYPESIZE_PD;
import static dk.dma.ais.abnormal.analyzer.config.Configuration.CONFKEY_ANALYSIS_TYPESIZE_PREDICTIONTIME_MAX;
import static dk.dma.ais.abnormal.analyzer.config.Configuration.CONFKEY_ANALYSIS_TYPESIZE_SHIPLENGTH_MIN;
import static dk.dma.ais.abnormal.event.db.domain.builders.ShipSizeOrTypeEventBuilder.ShipSizeOrTypeEvent;
import static dk.dma.ais.abnormal.util.AisDataHelper.nameOrMmsi;
import static dk.dma.ais.abnormal.util.TrackPredicates.isClassB;
import static dk.dma.ais.abnormal.util.TrackPredicates.isEngagedInTowing;
import static dk.dma.ais.abnormal.util.TrackPredicates.isFishingVessel;
import static dk.dma.ais.abnormal.util.TrackPredicates.isSmallVessel;
import static dk.dma.ais.abnormal.util.TrackPredicates.isSpecialCraft;
import static dk.dma.ais.abnormal.util.TrackPredicates.isUnknownTypeOrSize;

/**
 * This analysis manages events where the presence of a vessel of the given type
 * and size is "abnormal" for the current position (grid cell) relative to previous
 * observations for vessels in the same grid cell. Statistics for previous observations
 * are stored in the StatisticDataRepository.
 *
 * @author Thomas Borg Salling <tbsalling@tbsalling.dk>
 */
public class ShipTypeAndSizeAnalysis extends StatisticBasedAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(ShipTypeAndSizeAnalysis.class);

    private final AppStatisticsService statisticsService;

    private final int TOTAL_SHIP_COUNT_THRESHOLD;
    private final float PD;
    private final int SHIP_LENGTH_MIN;

    @Inject
    public ShipTypeAndSizeAnalysis(Configuration configuration, AppStatisticsService statisticsService, StatisticDataRepository statisticsRepository, EventEmittingTracker trackingService, EventRepository eventRepository, BehaviourManager behaviourManager) {
        super(eventRepository, statisticsRepository, trackingService, behaviourManager);
        this.statisticsService = statisticsService;

        setTrackPredictionTimeMax(configuration.getInteger(CONFKEY_ANALYSIS_TYPESIZE_PREDICTIONTIME_MAX, -1));

        TOTAL_SHIP_COUNT_THRESHOLD = configuration.getInt(CONFKEY_ANALYSIS_TYPESIZE_CELL_SHIPCOUNT_MIN, 1000);
        PD = configuration.getFloat(CONFKEY_ANALYSIS_TYPESIZE_PD, 0.001f);
        SHIP_LENGTH_MIN = configuration.getInt(CONFKEY_ANALYSIS_TYPESIZE_SHIPLENGTH_MIN, 50);
        LOG.info(getAnalysisName() + " created (" + this + ").");
    }

    @Override
    public String toString() {
        return "ShipTypeAndSizeAnalysis{" +
                "TOTAL_SHIP_COUNT_THRESHOLD=" + TOTAL_SHIP_COUNT_THRESHOLD +
                ", PD=" + PD +
                ", SHIP_LENGTH_MIN=" + SHIP_LENGTH_MIN +
                "} " + super.toString();
    }

    @AllowConcurrentEvents
    @Subscribe
    public void onCellIdChanged(CellChangedEvent trackEvent) {
        statisticsService.incAnalysisStatistics(getAnalysisName(), "Events received");

        Track track = trackEvent.getTrack();

        if (isClassB.test(track) || isUnknownTypeOrSize.test(track) || isFishingVessel.test(track) || isSmallVessel.test(track) || isSpecialCraft.test(track) || isEngagedInTowing.test(track)) {
            return;
        }

        /* Skip analysis if track has been predicted forward for too long */
        if (isLastAisTrackingReportTooOld(track, track.getTimeOfLastPositionReport())) {
            LOG.debug("Skipping analysis: MMSI " + track.getMmsi() + " was predicted for too long.");
            return;
        }

        Long cellId = (Long) track.getProperty(Track.CELL_ID);
        Integer shipType = track.getShipType();
        Integer shipLength = track.getVesselLength();

        if (cellId == null) {
            statisticsService.incAnalysisStatistics(getAnalysisName(), "Unknown mmsi");
            return;
        }

        if (shipType == null) {
            statisticsService.incAnalysisStatistics(getAnalysisName(), "Unknown ship type");
            return;
        }

        if (shipLength == null) {
            statisticsService.incAnalysisStatistics(getAnalysisName(), "Unknown ship length");
            return;
        }

        if (shipLength < SHIP_LENGTH_MIN) {
            statisticsService.incAnalysisStatistics(getAnalysisName(), "LOA < " + SHIP_LENGTH_MIN);
            return;
        }

        int shipTypeKey = Categorizer.mapShipTypeToCategory(shipType) - 1;
        int shipLengthKey = Categorizer.mapShipLengthToCategory(shipLength) - 1;

        if (isAbnormalCellForShipTypeAndSize(cellId, shipTypeKey, shipLengthKey)) {
            getBehaviourManager().abnormalBehaviourDetected(ShipSizeOrTypeEvent.class, track);
        } else {
            getBehaviourManager().normalBehaviourDetected(ShipSizeOrTypeEvent.class, track);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void onTrackStale(TrackStaleEvent trackEvent) {
        getBehaviourManager().trackStaleDetected(ShipSizeOrTypeEvent.class, trackEvent.getTrack());
        lowerExistingAbnormalEventIfExists(ShipSizeOrTypeEvent.class, trackEvent.getTrack());
    }

    @Subscribe
    public void onAbnormalEventRaise(AbnormalEventRaise behaviourEvent) {
        LOG.debug("onAbnormalEventRaise " + behaviourEvent.getTrack().getMmsi());
        if (behaviourEvent.getEventClass().equals(ShipSizeOrTypeEvent.class)) {
            raiseOrMaintainAbnormalEvent(ShipSizeOrTypeEvent.class, behaviourEvent.getTrack());
        }
    }
    @Subscribe
    public void onAbnormalEventMaintain(AbnormalEventMaintain behaviourEvent) {
        LOG.debug("onAbnormalEventMaintain " + behaviourEvent.getTrack().getMmsi());
        if (behaviourEvent.getEventClass().equals(ShipSizeOrTypeEvent.class)) {
            raiseOrMaintainAbnormalEvent(ShipSizeOrTypeEvent.class, behaviourEvent.getTrack());
        }
    }

    @Subscribe
    public void onAbnormalEventLower(AbnormalEventLower behaviourEvent) {
        LOG.debug("onAbnormalEventLower " + behaviourEvent.getTrack().getMmsi());
        if (behaviourEvent.getEventClass().equals(ShipSizeOrTypeEvent.class)) {
            lowerExistingAbnormalEventIfExists(ShipSizeOrTypeEvent.class, behaviourEvent.getTrack());
        }
    }

    /**
     * If the probability p(d)<PD and total count>TOTAL_SHIP_COUNT_THRESHOLD then abnormal. p(d)=sum(count)/count for all sog_intervals for
     * that shiptype and size.
     *
     * @param cellId
     * @param shipTypeKey
     * @param shipSizeKey
     * @return true if the presence of size/type in this cell is abnormal. False otherwise.
     */
    boolean isAbnormalCellForShipTypeAndSize(Long cellId, int shipTypeKey, int shipSizeKey) {
        float pd = 1.0f;

        StatisticData shipSizeAndTypeData = getStatisticDataRepository().getStatisticData("ShipTypeAndSizeStatistic", cellId);

        if (shipSizeAndTypeData instanceof ShipTypeAndSizeStatisticData) {
            Integer totalCount  = ((ShipTypeAndSizeStatisticData) shipSizeAndTypeData).getSumFor("shipCount");
            if (totalCount > TOTAL_SHIP_COUNT_THRESHOLD) {
                Integer shipCount = ((ShipTypeAndSizeStatisticData) shipSizeAndTypeData).getValue(shipTypeKey, shipSizeKey, ShipTypeAndSizeStatisticData.STAT_SHIP_COUNT);
                if (shipCount == null) {
                    shipCount = 0;
                }
                pd = (float) shipCount / (float) totalCount;
                LOG.debug("cellId=" + cellId + ", shipType=" + shipTypeKey + ", shipSize=" + shipSizeKey + ", shipCount=" + shipCount + ", totalCount=" + totalCount + ", pd=" + pd);
            } else {
                LOG.debug("totalCount of " + totalCount + " is not enough statistical data for cell " + cellId);
            }
        }

        LOG.debug("pd = " + pd);

        boolean isAbnormalCellForShipTypeAndSize = pd < PD;
        if (isAbnormalCellForShipTypeAndSize) {
            LOG.debug("Abnormal event detected.");
        } else {
            LOG.debug("Normal or inconclusive event detected.");
        }

        statisticsService.incAnalysisStatistics(getAnalysisName(), "Analyses performed");

        return isAbnormalCellForShipTypeAndSize;
    }

    @Override
    protected Event buildEvent(Track track, Track... otherTracks) {
        if (otherTracks != null && otherTracks.length > 0) {
            throw new IllegalArgumentException("otherTracks not supported.");
        }

        Integer mmsi = track.getMmsi();
        Integer imo = track.getIMO();
        String callsign = track.getCallsign();
        String name = nameOrMmsi(track.getShipName(), mmsi);
        Integer shipType = track.getShipType();
        Integer shipLength = track.getVesselLength();
        Integer shipDimensionToBow = track.getShipDimensionBow();
        Integer shipDimensionToStern = track.getShipDimensionStern();
        Integer shipDimensionToPort = track.getShipDimensionPort();
        Integer shipDimensionToStarboard = track.getShipDimensionStarboard();
        LocalDateTime positionTimestamp = track.getTimeOfLastPositionReportTyped();
        Position position = track.getPosition();
        Float cog = track.getCourseOverGround();
        Float sog = track.getSpeedOverGround();
        Float hdg = track.getTrueHeading();
        Boolean interpolated = track.getNewestTrackingReport() instanceof InterpolatedTrackingReport;

        TrackingPoint.EventCertainty certainty = TrackingPoint.EventCertainty.UNDEFINED;
        EventCertainty eventCertainty = getBehaviourManager().getEventCertaintyAtCurrentPosition(ShipSizeOrTypeEvent.class, track);
        if (eventCertainty != null) {
            certainty = TrackingPoint.EventCertainty.create(eventCertainty.getCertainty());
        }

        short shipTypeCategory = Categorizer.mapShipTypeToCategory(shipType);
        short shipLengthCategory = Categorizer.mapShipLengthToCategory(shipLength);

        String shipTypeAsString = Categorizer.mapShipTypeCategoryToString(shipTypeCategory);
        String shipLengthAsString = Categorizer.mapShipSizeCategoryToString(shipLengthCategory);

        String title = "Abnormal type and size of ship";
        String description = String.format("Abnormal type and size of " + name + " (" + shipTypeAsString + ") on position " + position + " at " + DATE_FORMAT.format(positionTimestamp) + ": type:%d(%s) size:%d(%s).", shipType, shipTypeAsString, shipLength, shipLengthAsString);

        LOG.info(description);

        Event event =
                ShipSizeOrTypeEvent()
                    .shipType(shipTypeCategory)
                    .shipLength(shipLengthCategory)
                    .title(title)
                    .description(description)
                    .startTime(positionTimestamp)
                    .behaviour()
                        .isPrimary(true)
                        .vessel()
                            .mmsi(mmsi)
                            .imo(imo)
                            .callsign(callsign)
                            .type(shipType /* shipTypeCategory */)
                            .toBow(shipDimensionToBow)
                            .toStern(shipDimensionToStern)
                            .toPort(shipDimensionToPort)
                            .toStarboard(shipDimensionToStarboard)
                            .name(name)
                        .trackingPoint()
                            .timestamp(positionTimestamp)
                            .positionInterpolated(interpolated)
                            .eventCertainty(certainty)
                            .speedOverGround(sog)
                            .courseOverGround(cog)
                            .trueHeading(hdg)
                            .latitude(position.getLatitude())
                            .longitude(position.getLongitude())
                .getEvent();

        addPreviousTrackingPoints(event, track);

        statisticsService.incAnalysisStatistics(getAnalysisName(), "Events raised");

        return event;
    }
}
