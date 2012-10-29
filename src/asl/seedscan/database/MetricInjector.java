/**
 * 
 */
package asl.seedscan.database;

import java.util.logging.Logger;

import asl.concurrent.Task;
import asl.concurrent.TaskThread;
import asl.seedscan.metrics.MetricResult;

/**
 * @author Joel D. Edwards <jdedwards@usgs.gov>
 *
 */
public class MetricInjector
extends TaskThread<MetricResult>
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.database.MetricInjector");
    
	MetricDatabase metricDB;
	
	/**
	 * 
	 */
	public MetricInjector(MetricDatabase metricDB) {
		super();
		this.metricDB = metricDB;
	}

	/**
	 * @param capacity
	 */
	public MetricInjector(MetricDatabase metricDB, int capacity) {
		super(capacity);
		this.metricDB = metricDB;
	}

	/* (non-Javadoc)
	 * @see asl.concurrent.TaskThread#setup()
	 */
	@Override
	protected void setup() {
		// Pre-run logic goes here
	}

	/* (non-Javadoc)
	 * @see asl.concurrent.TaskThread#performTask(asl.concurrent.Task)
	 */
	@Override
	protected void performTask(Task<MetricResult> task) {
		String command = task.getCommand();
		MetricResult results = task.getData();
		
		logger.info("performTask: command=" + command + " results=" + results);
		
		if (command.equals("INJECT")) {
			metricDB.insertMetricData(results);
		}
	}

	/* (non-Javadoc)
	 * @see asl.concurrent.TaskThread#cleanup()
	 */
	@Override
	protected void cleanup() {
		// Post-run logic goes here
	}

	public void inject(MetricResult results)
	throws InterruptedException
	{
		addTask("INJECT", results);
	}
}
