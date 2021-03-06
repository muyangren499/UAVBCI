package com.DroneSimulator;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;



import nl.fcdonders.fieldtrip.bufferclient.*;

public class Simulation {
	public static final int BUFFER_PORT = 1972;
	//public static final String BUFFER_HOSTNAME = "131.174.106.82"; //3
	public static final String BUFFER_HOSTNAME = "131.174.106.81"; //2
//	public static final String BUFFER_HOSTNAME = "localhost";
	private static final int SHORT_BREAK_TIME = 5;
	private static final int LONG_BREAK_TIME = 30;
	public static final int TRIAL_LENGTH = 5000;

	// These represent the constant values of the simulation.
	private double velocity, duration;

	// These represent the maximum starting distance
	// of the simulated drone from the X/Y axis respectively.
	private double devianceX, devianceY;

	// These represent the initial dimensions of the target.
	private double initialTargetHeight, initialTargetWidth;

	// Used to generate the random start positions.
	private Random random;

	// Used to find the screensize.
	private Dimension dim = null;

	private Screen screen;

	private BufferedWriter dataOut;
	private BufferedWriter dataOut2;
	private static String headerLine = "isHit,timeRequired,startX,startY,targetWidth,targetHeight,score, droneY";
	private ArrayList<Integer> dronePositionsY= new ArrayList<Integer>();
	private ArrayList<Integer> dronePositionsX= new ArrayList<Integer>();

	// private TrialParameters last = null;

	private int shortBreakTrials = 5;
	private int longBreakTrials = 25;
	private double totalTrials = 50;

	private int cursorDistance = 4 * Screen.STEPSIZE;
	private int minTargetSize = Screen.STEPSIZE;

	private double hits;
	private double score;
	private double sizeDecrease = 0.2 * Screen.STEPSIZE;
	private double hitRateConv = 0.80;
	private double sizeIncrease = (int) (sizeDecrease / ((1 - hitRateConv) / hitRateConv));

	// bufferstuff
	private BufferClientClock c;
	private Header hdr;
	private boolean bufferConnected = false;
	
	private Thread t;
	private Thread t2;

	public Simulation(Screen s) throws IOException {

		this.screen = s;
		random = new Random();
		velocity = duration = devianceX = devianceY = 0;
		// xHits = yHits = totalTrials = 0;
		initialTargetHeight = initialTargetWidth = 2 * Screen.STEPSIZE;
		dim = Toolkit.getDefaultToolkit().getScreenSize();

		connectBuffer();
		t = new Thread(new BufferReader(screen, "classifier.prediction"));
		//t = new Thread(new BufferReader(screen, "shrdcontrol.prediction"));
		t.start();
		
		//t2 = new Thread(new JoystickHandler());
		//t2.start();
		
		

		startExperiment();

	}

	private void connectBuffer() {
		String hostname = BUFFER_HOSTNAME;
		int port = BUFFER_PORT;

		c = new BufferClientClock();

		hdr = null;
		while (hdr == null) {
			try {
				System.out.println("Connecting to " + hostname + ":" + port);
				c.connect(hostname, port);
				// C.setAutoReconnect(true);
				if (c.isConnected()) {
					System.out.println("GETHEADER");
					hdr = c.getHeader();
				}
			} catch (IOException e) {
				hdr = null;
			}
			if (hdr == null) {
				System.out.println("Invalid Header... waiting");
				sleep(1000);
			}
			bufferConnected = true;
			// Print stuff
			System.out.println("#channels....: " + hdr.nChans);
			System.out.println("#samples.....: " + hdr.nSamples);
			System.out.println("#events......: " + hdr.nEvents);
			System.out.println("Sampling Freq: " + hdr.fSample);
			System.out.println("data type....: " + hdr.dataType);
			for (int n = 0; n < hdr.nChans; n++) {
				if (hdr.labels[n] != null) {
					System.out.println("Ch. " + n + ": " + hdr.labels[n]);
				}
			}
		}
	}

	public void startExperiment() throws IOException {
		/*Date dt = Calendar.getInstance().getTime();
		String filename = dt.getHours() + "-" + dt.getMinutes() + "-"
				+ dt.getSeconds() + ".csv";
		this.dataOut = new BufferedWriter(new FileWriter("/home/uavbci/Desktop/" + filename));
		this.dataOut.write(headerLine);
		this.dataOut.newLine();
		
		String filename2 = "XY" +dt.getHours() + "-" + dt.getMinutes() + "-"
				+ dt.getSeconds() + ".csv";
		
		this.dataOut2 = new BufferedWriter(new FileWriter("/home/uavbci/Desktop/" + filename2));
		//this.dataOut.write(headerLine);
		this.dataOut.newLine();
		*/
		System.out.println("startEXP");
		
		int trialcounter = 0;
		hits = 0;
		long starttime;
		long endtime;
		screen.setState(Screen.States.TRIAL_BREAK);
		screen.startCountdown(LONG_BREAK_TIME);
		while (trialcounter < totalTrials) {
			
			
			if (trialcounter % longBreakTrials == 0 && trialcounter != 0) {
				screen.setState(Screen.States.TRIAL_BREAK);
				if (bufferConnected)
				{
					System.out.println("Sending break.");
					c.putEvent(new BufferEvent("Break", LONG_BREAK_TIME, -1));
					System.out.println("Sent break!");
				}
				screen.startCountdown(LONG_BREAK_TIME);

			} else if (trialcounter % shortBreakTrials == 0 && trialcounter != 0) {
				screen.setState(Screen.States.TRIAL_BREAK);
				if (bufferConnected)
				{
					System.out.println("Sending break.");
					c.putEvent(new BufferEvent("Break", SHORT_BREAK_TIME, -1));
					System.out.println("Sent break!");
				}
				screen.startCountdown(SHORT_BREAK_TIME);
			}
			
			if (trialcounter == 0) {
				screen.setCurrentTrial(generateInitial());
			}
			screen.setState(Screen.States.TRIAL_EMPTY);

			sleep(randomBreakTime());
			starttime = System.currentTimeMillis();
			screen.setState(Screen.States.TRIAL_BUSY);
			screen.reset();
			if (bufferConnected)
			{
				System.out.println("Sending startPhase.cmd...");
				//c.putEvent(new BufferEvent("TrialStart", "", -1));
				c.putEvent(new BufferEvent("startPhase.cmd","testing", trialcounter));
				System.out.println("Sent startPhase.cmd!");
			}
			screen.showProgressBar();

			sleep(2000);
			screen.setState(Screen.States.TRIAL_CLASSIFYING);
			System.out.println("Statechange at " + (System.currentTimeMillis() - starttime));
			sleep(TRIAL_LENGTH - 2000);
			if (bufferConnected)
			{
				System.out.println("Sending testing...");
				//c.putEvent(new BufferEvent("TrialEnd", "", -1));
				c.putEvent(new BufferEvent("testing", "end", -1));
				System.out.println("Sent testing!");
			}
			if (screen.isHit()) {
				hits++;
			}
			endtime = System.currentTimeMillis();
			System.out.println("Trial duration: " + (endtime - starttime));
			
			screen.setCurrentTrial(generateNext());
			System.out.println("Trial completed: " + trialcounter);
			dronePositionsY = screen.getDronePositionsY();
			dronePositionsX = screen.getDronePositionsX();
			
			submitResult();
			trialcounter++;
			
		}
		screen.setState(Screen.States.TRIAL_END);
		// TODO change (because deprecated and unsafe)
		t.stop();
		//this.dataOut.close();
		//this.dataOut2.close();

		printResults();
	}

	private void printResults() {
		System.out.println("Experiment Completed! :D");
		System.out.println("Total trials: " + totalTrials);
		System.out.println("Total number of hits: " + hits);
		score = ((screen.getCurrentTrial().getTargetHeight()) - minTargetSize)
				/ ((cursorDistance - minTargetSize) / 10);
		System.out.format("Performance on a scale of %d to %d: %f%n",
				cursorDistance, minTargetSize, screen.getCurrentTrial()
						.getTargetHeight());
		// System.out.printf("Performance on a scale of %i to %i")
		// System.out.println("Performance on a scale of 200 to 50: " +
		// screen.getCurrentTrial().getTargetHeight());
		System.out.println("Score on a scale of 10 to 0: " + score);
	}

	private TrialParameters generateInitial() { // Create the first simulation
												// run
		TrialParameters last = new TrialParameters();
		double angle = random.nextDouble() * 360;
		last.setStartX(dim.width / 2 + cursorDistance * Math.cos(angle));
		//+ cursorDistance * Math.cos(angle)
		last.setStartY(dim.height / 2  + cursorDistance * Math.sin(angle));
		last.setTargetWidth(initialTargetWidth);
		last.setTargetHeight(initialTargetHeight);
		return last;
	}

	public TrialParameters generateNext() {
		double lastWidth, lastHeight;
		// Get the size of the target from the last trial.
		TrialParameters last = screen.getCurrentTrial();
		if (last != null) {
			lastWidth = last.getTargetWidth();
			lastHeight = last.getTargetHeight();
		} else {
			lastWidth = initialTargetWidth;
			lastHeight = initialTargetHeight;
		}
		System.out.println(lastWidth);
		// Calculate new size based on the Weighted Up-Down method.
		
		double newWidth, newHeight;
		if (screen.isHit()){
			newWidth = lastWidth - sizeDecrease;
			newHeight = lastHeight - sizeDecrease;
			if(newWidth < minTargetSize	&& newHeight < minTargetSize){
				newWidth = minTargetSize;
				newHeight = minTargetSize;
			}
		} 
		else{
			newWidth = lastWidth + (sizeIncrease * (1 - (hits / totalTrials)));
			newHeight = lastHeight + (sizeIncrease * (1 - (hits / totalTrials)));
			if(newWidth > cursorDistance && newHeight > cursorDistance)
			{
				newWidth = cursorDistance;
				newHeight = cursorDistance;
			}
		}
		
		/*double newWidth, newHeight;
		if (screen.isHit() && lastWidth > minTargetSize	&& lastHeight > minTargetSize) {
			newWidth = lastWidth - sizeDecrease;
			newHeight = lastHeight - sizeDecrease;
		} else if (screen.isHit() && lastWidth <= (minTargetSize + sizeDecrease) && lastHeight <= (minTargetSize + sizeDecrease)) {
			newWidth = minTargetSize;
			newHeight = minTargetSize;
		} else if (lastWidth <= cursorDistance && lastHeight <= cursorDistance) {
			newWidth = lastWidth + (sizeIncrease * (1 - (hits / totalTrials)));
			newHeight = lastHeight + (sizeIncrease * (1 - (hits / totalTrials)));
		} else {
			newWidth = cursorDistance;
			newHeight = cursorDistance;
		}*/
		// Create the new simulation run object.
		TrialParameters run = new TrialParameters();
		double angle = random.nextDouble() * 360;
		run.setStartX(dim.width / 2 + cursorDistance * Math.cos(angle));
		//+ 
		run.setStartY(dim.height / 2 );
		//+ cursorDistance * Math.sin(angle)
		run.setTargetWidth(newWidth);
		run.setTargetHeight(newHeight);
		return run;
	}

	/**
	 * Adds the provided SimulationRun to the saved records.
	 * 
	 * @param run
	 *            The run to save.
	 * @return
	 * @throws IOException 
	 */
	public void reportSimulationResults(TrialResults run) throws IOException {
		try {
			dataOut.write(run.isHit() ? "true" : "false");
			dataOut.write(",");
			dataOut.write(Double.toString(run.getTimeRequired()));
			dataOut.write(",");
			dataOut.write(Double.toString(run.getSimulationDetails()
					.getStartX()));
			dataOut.write(",");
			dataOut.write(Double.toString(run.getSimulationDetails()
					.getStartY()));
			dataOut.write(",");
			dataOut.write(Double.toString(run.getSimulationDetails()
					.getTargetWidth()));
			dataOut.write(",");
			dataOut.write(Double.toString(run.getSimulationDetails()
					.getTargetHeight()));
			dataOut.write(",");
			dataOut.write(Double.toString(score));
			

			dataOut.flush();
		} catch (IOException ex) {
			// TODO: Handle
		}
		
		Iterator<Integer> itY = dronePositionsY.iterator();
		Iterator<Integer> itX = dronePositionsX.iterator();
		dataOut2.write(Double.toString(run.getSimulationDetails()
				.getStartY()));
		try {
			while(itY.hasNext())
			{
				
				dataOut2.write(",");
				dataOut2.write(itY.next().toString());
			}
			dataOut2.newLine();
			dataOut2.write(Double.toString(run.getSimulationDetails()
					.getStartX()));
			while(itX.hasNext())
			{
				
				dataOut2.write(",");
				dataOut2.write(itX.next().toString());
			}
			
			screen.resetDronePos();
			
			dataOut2.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void submitResult() {
		TrialResults result = new TrialResults();
		result.setHit(screen.isHit());
		// result.setTimeRequired(1337); // TODO: change
		result.setSimulationDetails(screen.getCurrentTrial());
		try {
			reportSimulationResults(result);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// TODO put in loop: simulation.reportSimulationResults(result);
	}

	public double getDevianceY() {
		return devianceY;
	}

	public void setDevianceY(double devianceY) {
		this.devianceY = devianceY;
	}

	public double getDevianceX() {
		return devianceX;
	}

	public void setDevianceX(double devianceX) {
		this.devianceX = devianceX;
	}

	public double getVelocity() {
		return velocity;
	}

	public void setVelocity(double velocity) {
		this.velocity = velocity;
	}

	public double getDuration() {
		return duration;
	}

	public void setDuration(double duration) {
		this.duration = duration;
	}

	public double getInitialTargetHeight() {
		return initialTargetHeight;
	}

	public void setInitialTargetHeight(double initialTargetHeight) {
		this.initialTargetHeight = initialTargetHeight;
	}

	public double getInitialTargetWidth() {
		return initialTargetWidth;
	}

	public void setInitialTargetWidth(double initialTargetWidth) {
		this.initialTargetWidth = initialTargetWidth;
	}

	public void setShortBreakTrials(int shortBreakTrials) {
		this.shortBreakTrials = shortBreakTrials;
	}

	public void setLongBreakTrials(int longBreakTrials) {
		this.longBreakTrials = longBreakTrials;
	}

	/**
	 * 
	 * @return random number between 500 and 2500
	 */
	private long randomBreakTime() {
		Random rand = new Random();
		return rand.nextInt(2000) + 500;
	}

	public static void sleep(long ms) {
//		try {
//			Thread.sleep(ms);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() < (startTime + ms))
		{
		
		}
		
	}

}
