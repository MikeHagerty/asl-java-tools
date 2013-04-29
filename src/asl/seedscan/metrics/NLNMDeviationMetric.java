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

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.nio.ByteBuffer;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Calendar;

import asl.metadata.Channel;
import asl.metadata.Station;
import asl.util.Hex;
import asl.util.PlotMaker;

import timeutils.Timeseries;

/**
 * NLNMDeviationMetric - Compute Difference (over specified range of periods = powerband) between
 *                       the power spectral density (psd) of a channel and the NLNM.
 */
public class NLNMDeviationMetric
extends PowerBandMetric
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.metrics.NLNMDeviationMetric");

    @Override public long getVersion()
    {
        return 1;
    }

    @Override public String getBaseName()
    {
        return "NLNMDeviationMetric";
    }

    public NLNMDeviationMetric(){
        super();
        addArgument("nlnm-modelfile");
    }

    private double[] NLNMPeriods;
    private double[] NLNMPowers;
    private double[] NHNMPeriods;
    private double[] NHNMPowers;
    private final String outputDir = "outputs";
    private Boolean highNoiseModelExists = false;

    public void process()
    {
        System.out.format("\n              [ == Metric %s == ]    [== Station %s ==]    [== Day %s ==]\n", 
                          getName(), getStation(), getDay() );

   // Read in the NLNM or return
        if (!readNLNM() ){
            System.out.format("%s: Did not read in NLNM model --> Do Nothing!\n", getName());
            return;  // Can't do anything if we didn't read in a NLNM model so skip to the next metric
        }
        highNoiseModelExists = readNHNM();

        ArrayList<Channel> channels = stationMeta.getChannelArray("LH"); // Get all LH channels in metadata

   // Loop over channels, get metadata & data for channel and Calculate Metric

        for (Channel channel : channels){

         // Check to see that we have data + metadata & see if the digest has changed wrt the database:
         // Update: At this point we KNOW we have metadata since it is driving the channel loop

            ByteBuffer digest = metricData.valueDigestChanged(channel, createIdentifier(channel), getForceUpdate() );

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

     // Compute/Get the 1-sided psd[f] using Peterson's algorithm (24 hrs, 13 segments, etc.)

        CrossPower crossPower = getCrossPower(channel, channel);
        double[] psd  = crossPower.getSpectrum();
        double df     = crossPower.getSpectrumDeltaF();

     // nf = number of positive frequencies + DC (nf = nfft/2 + 1, [f: 0, df, 2df, ...,nfft/2*df] )
        int nf        = psd.length;
        double freq[] = new double[nf];

     // Fill freq array & Convert spectrum to dB
        for ( int k = 0; k < nf; k++){
            freq[k] = (double)k * df;
            psd[k]  = 10.*Math.log10(psd[k]);
        }

     // Convert psd[f] to psd[T]
     // Reverse freq[] --> per[] where per[0]=shortest T and per[nf-2]=longest T:

        double[] per    = new double[nf];
        double[] psdPer = new double[nf];
     // per[nf-1] = 1/freq[0] = 1/0 = inf --> set manually:
        per[nf-1] = 0;  
        for (int k = 0; k < nf-1; k++){
            per[k]     = 1./freq[nf-k-1];
            psdPer[k]  = psd[nf-k-1];
        }
        double Tmin  = per[0];    // Should be = 1/fNyq = 2/fs = 0.1 for fs=20Hz
        double Tmax  = per[nf-2]; // Should be = 1/df = Ndt

        String outFile; // Use for outputting spectra arrays (in testing)

        //outFile = channel.toString() + ".psd.T";
        //Timeseries.timeoutXY(per, psdPer, outFile);

     // Interpolate the smoothed psd to the periods of the NLNM Model:
        double psdInterp[] = Timeseries.interpolate(per, psdPer, NLNMPeriods);

        //outFile = channel.toString() + ".psd.Fsmooth.T.Interp";
        //Timeseries.timeoutXY(NLNMPeriods, psdInterp, outFile);

        PowerBand band    = getPowerBand();
        double lowPeriod  = band.getLow();
        double highPeriod = band.getHigh();

        if (!checkPowerBand(lowPeriod, highPeriod, Tmin, Tmax)){
            System.out.format("%s powerBand Error: Skipping channel:%s\n", getName(), channel);
            return NO_RESULT;
        }

    // Compute deviation from NLNM within the requested period band:
        double deviation = 0;
        int nPeriods = 0;
        for (int k = 0; k < NLNMPeriods.length; k++){
            if (NLNMPeriods[k] >  highPeriod){
                break;
            }
            else if (NLNMPeriods[k] >= lowPeriod){
                double difference = psdInterp[k] - NLNMPowers[k];
                //deviation += Math.sqrt( Math.pow(difference, 2) );
                deviation += difference;
                nPeriods++;
            }
        }

        if (nPeriods == 0) {
            StringBuilder message = new StringBuilder();
            message.append(String.format("NLNMDeviation Error: Requested band [%f - %f] contains NO periods within NLNM\n"
                ,lowPeriod, highPeriod) );
            throw new RuntimeException(message.toString());
        }
        deviation = deviation/(double)nPeriods;

        if (getMakePlots()) {   // Output files like 2012160.IU_ANMO.00-LHZ.png = psd
            PlotMaker plotMaker = new PlotMaker(metricResult.getStation(), channel, metricResult.getDate());
            if (highNoiseModelExists){
                plotMaker.plotPSD(NLNMPeriods, NLNMPowers, NHNMPeriods, NHNMPowers, psdInterp, "NLNM", "psd-nlnm");
            }
            else {
                plotMaker.plotPSD(NLNMPeriods, NLNMPowers, psdInterp, "NLNM", "psd-nlnm");
            }
        }

        return deviation;

    } // end computeMetric()


/** readNLNM() - Read in Peterson's NewLowNoiseModel from file specified in config.xml
 **       e.g., <cfg:argument cfg:name="nlnm-modelfile">/Users/mth/mth/Projects/xs0/NLNM.ascii/</cfg:argument>
 **             NLNM Periods will be read into NLNMPeriods[]
 **             NLNM Powers  will be read into NLNMPowers[]
 **/

    private Boolean readNLNM() {

        String fileName = null;
        try {
            fileName = get("nlnm-modelfile");
        } catch (NoSuchFieldException ex) {
          System.out.format("%s Error: Model Name ('model') was not specified in config.xml!\n", getName());
          return false;
        }

   // First see if the file exists
        if (!(new File(fileName).exists())) {
            System.out.format("=== %s: NLNM file=%s does NOT exist!\n", getName(), fileName);
            return false;
        }
   // Temp ArrayList(s) to read in unknown number of (x,y) pairs:
        ArrayList<Double> tmpPers = new ArrayList<Double>();
        ArrayList<Double> tmpPows = new ArrayList<Double>();
        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(fileName));
            while ((line = br.readLine()) != null) {
                String[] args = line.trim().split("\\s+") ;
                if (args.length != 2) {
                    String message = "==Error reading NLNM: got " + args.length + " args on one line!";
                    throw new RuntimeException(message);
                }
                tmpPers.add( Double.valueOf(args[0].trim()).doubleValue() );
                tmpPows.add( Double.valueOf(args[1].trim()).doubleValue() );
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        Double[] modelPeriods  = tmpPers.toArray(new Double[]{});
        Double[] modelPowers   = tmpPows.toArray(new Double[]{});

        NLNMPeriods = new double[modelPeriods.length];
        NLNMPowers  = new double[modelPowers.length];

        for (int i=0; i<modelPeriods.length; i++){
            NLNMPeriods[i] = modelPeriods[i];
            NLNMPowers[i]  = modelPowers[i];
        }

        return true;

    } // end readNLNM

    private Boolean readNHNM() {

        String fileName = "/Users/mth/mth/Projects/asl/resources/NHNM.ascii";
/**
        try {
            fileName = get("nlnm-modelfile");
        } catch (NoSuchFieldException ex) {
          System.out.format("%s Error: Model Name ('model') was not specified in config.xml!\n", getName());
          return false;
        }
**/

   // First see if the file exists
        if (!(new File(fileName).exists())) {
            System.out.format("=== %s: NLNM file=%s does NOT exist!\n", getName(), fileName);
            return false;
        }
   // Temp ArrayList(s) to read in unknown number of (x,y) pairs:
        ArrayList<Double> tmpPers = new ArrayList<Double>();
        ArrayList<Double> tmpPows = new ArrayList<Double>();
        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(fileName));
            while ((line = br.readLine()) != null) {
                String[] args = line.trim().split("\\s+") ;
                if (args.length != 2) {
                    String message = "==Error reading NLNM: got " + args.length + " args on one line!";
                    throw new RuntimeException(message);
                }
                tmpPers.add( Double.valueOf(args[0].trim()).doubleValue() );
                tmpPows.add( Double.valueOf(args[1].trim()).doubleValue() );
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        Double[] modelPeriods  = tmpPers.toArray(new Double[]{});
        Double[] modelPowers   = tmpPows.toArray(new Double[]{});

        NHNMPeriods = new double[modelPeriods.length];
        NHNMPowers  = new double[modelPowers.length];

        for (int i=0; i<modelPeriods.length; i++){
            NHNMPeriods[i] = modelPeriods[i];
            NHNMPowers[i]  = modelPowers[i];
        }

        return true;

    } // end readHLNM


} // end class

