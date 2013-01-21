/*
 * Copyright 2012, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */
package asl.seedscan.metrics;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;

import java.nio.ByteBuffer;
import asl.util.Hex;

import asl.metadata.*;
import asl.metadata.meta_new.*;
import asl.seedsplitter.*;

public class CalibrationMetric
extends Metric
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.metrics.CalibrationMetric");

    @Override public long getVersion()
    {
        return 1;
    }

    @Override public String getName()
    {
        return "CalibrationMetric";
    }


    public void process()
    {
        System.out.format("\n              [ == Metric %s == ]\n", getName() ); 

    // Get a sorted list of continuous channels for this stationMeta and loop over:
        ArrayList<Channel> channels = stationMeta.getContinuousChannels();

        for (Channel channel : channels){

            ByteBuffer digest = metricData.valueDigestChanged(channel, createIdentifier(channel));

            //logger.fine(String.format("%s: digest=%s\n", getName(), (digest == null) ? "null" : Hex.byteArrayToHexString(digest.array())));

        // At this point we KNOW we have metadata so we WILL compute a digest.  If the digest is null
        //  then nothing has changed and we don't need to recompute the metric
            if (digest == null) { 
                System.out.format("%s INFO: Data and metadata have NOT changed for this channel:%s --> Skipping\n"
                                ,getName(), channel);
                continue;
            }

            double result = computeMetric(channel);

            if (result == NO_RESULT) {
                // Do nothing --> skip to next channel
            }
            else {
                metricResult.addResult(channel, result, digest);
            }

        }// end foreach channel
    } // end process()

    private double computeMetric(Channel channel) {

        if (!metricData.hasChannelData(channel)) {
            return NO_RESULT;
        }

        ArrayList<Integer> qualities = metricData.getChannelQualityData(channel);

        if (qualities == null) {
            return NO_RESULT;
        }

        int totalQuality = 0;
        int totalPoints  = 0;

        for (int i=0; i<qualities.size(); i++){
            totalQuality += qualities.get(i);
            totalPoints++;
//System.out.format("== TimingQuality: quality[%d] = %d\n", i, qualities.get(i) );
        } 

        double averageQuality = 0.;

        if (totalPoints > 0) {
            averageQuality = (double)totalQuality / (double)totalPoints;
        }
        else {
            System.out.format("== TimingQualityMetric: WARNING: We have NO timing quality measurements for channel=[%s] = 0!!\n",
                                channel);
            return NO_RESULT;
        }

        return averageQuality;

    } // end computeMetric()

}
