package org.firstinspires.ftc.teamcode.FoundationPipeline;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class Pipeline {
	
	private static final int regionSideClipExtensionLength = 120; //120
	
    public static Mat resizedImage = new Mat();

    public static List<Foundation> foundations = new ArrayList<Foundation>();
    public static List<Stone>      stones      = new ArrayList<Stone>();
    public static List<SkyStone>   skyStones   = new ArrayList<SkyStone>();

    //debug steps
    public static Mat red    = new Mat();
    public static Mat blue   = new Mat();
    public static Mat yellow = new Mat();
    public static Mat black  = new Mat();
    public static int blackcut=0;

    public static boolean doFoundations = true;
    public static boolean doStones = true;
    public static boolean doSkyStones = true;

    public static Mat process(Mat source0){
        return process(source0, 640, 480);
        //return process(source0, 3264/4, 2448/4);
    }

    /**
     * Give it the raw image and it will update the Foundations arraylist
     *
     * @return source image with annotations on it
     */
    public static Mat process(Mat source0, double width, double height) {
        System.gc();
        System.runFinalization();

        //default image size: 3264 x 2448
        Imgproc.resize(source0, resizedImage, new Size(width*1, height*1), 0.0, 0.0, Imgproc.INTER_LINEAR);
        //Start.display(resizedImage,1,"input");

        //white balance


        Mat original = resizedImage.clone();

        //set ranges
        double blackCutOff = compute.getHistogramfast(resizedImage);
        blackcut= (int)blackCutOff;

        //For Blue
        double[] blueRange1 = {170,180};
        double[] blueRange2 = {0,10};

        //for Red
        double[] redRange = {110,120};


        /*
        //For yellow
        double[] yellowRange = {73,86};

        //For Blue
        double[] blueRange1 = {160,180};
        double[] blueRange2 = {0,20};

        //for Red
        double[] redRange = {40,63};
        */

        double[] satRange = {80, 255};
        double[] valRange = {blackCutOff*0.7, 255};

        Mat redOutput = compute.threshold(resizedImage, redRange, satRange, valRange);

        Mat blueOutput = compute.combine(
                compute.threshold(resizedImage, blueRange1, satRange, valRange),
                compute.threshold(resizedImage, blueRange2, satRange, valRange));

        Mat blackOutput = compute.threshold(
                resizedImage,
                new double[]{0, 255},//hue  0, 180
                new double[]{0, 180},//sat  0, 180
                new double[]{0, blackCutOff*0.8});//val

        //For yellow
        double[] yellowRange = {13,26};

        //yellow stones face sideways, so there is less glare
        //thus the saturation minimum can be higher
        Mat yellowOutput = compute.threshold(
        		resizedImage,
        		yellowRange,
        		new double[]{150, 255},//sat
                new double[]{blackCutOff*1.4, 255}); //val

        Mat yellowTags = compute.threshold(
        		resizedImage,
        		new double[]{10,37},
        		new double[]{100, 255},//sat
                new double[]{blackCutOff*0.7, 255}); //val

        //For debug display
        red = redOutput.clone();
        blue = blueOutput.clone();
        yellow = yellowOutput.clone();
        black = blackOutput.clone();

        skyStones.clear();
        stones.clear();
        foundations.clear();

        if(doSkyStones) skyStones = computeSkyStones(yellowTags,original);
        if(doStones) stones = computeStones(yellowOutput, original);
        if(doFoundations) foundations = computeFoundations(redOutput, blueOutput, yellowOutput, blackOutput, original);

        for (Stone s : stones) {
            s.draw(original);
        }for (Foundation f : foundations) {
            f.draw(original);
        }for (SkyStone s : skyStones) {
            s.draw(original);
        }

        redOutput.release();
        blueOutput.release();
        yellowOutput.release();
        blackOutput.release();

        return original;
    }

    static List<SkyStone> computeSkyStones(Mat yellowTags, Mat canvas){
    	List<SkyStone> skyStones = new ArrayList<SkyStone>();

        List<MatOfPoint> hulls = compute.findHulls(yellowTags);
        compute.drawHulls(hulls,yellowTags, new Scalar(255,255,255),2);

        Mat drawInternalHulls = new Mat(yellowTags.rows(), yellowTags.cols(), CvType.CV_8UC3);

        Imgproc.rectangle(drawInternalHulls,
        		new Point(0,0),
        		new Point(drawInternalHulls.width(),drawInternalHulls.height()),
        		new Scalar(0,0,0),
        		-1);

        yellowTags = compute.flip(yellowTags);

        List<MatOfPoint> internalHulls = compute.findHulls(yellowTags);
        internalHulls = compute.filterContours(internalHulls,1700);

        compute.drawHulls(internalHulls,drawInternalHulls);

        for(MatOfPoint h : internalHulls) {
        	skyStones.add(new SkyStone(h));
        }

        return skyStones;
    }

    /*
     * Takes in yellow masks, and image to annotate on
     * spits out list of Stones
     */
	static List<Stone> computeStones(Mat yellowOutput, Mat canvas){
    	Mat dTrans = compute.distanceTransform(yellowOutput,12);
        //Start.display(dTrans,1,"trans");

    	List<MatOfPoint> stonesContour = compute.findHulls(dTrans);

        List<Stone> stones = new ArrayList<Stone>();

        for(MatOfPoint con : stonesContour) {
        	Stone d = new Stone(con);
        	if(!d.isBastard) {
        		stones.add(d);
        	}
        }

        return stones;
    }

    /*
     * Takes in red, blue, yellow, black masks, and image to annotate on
     * spits out list of Foundations
     */
    static List<Foundation> computeFoundations(Mat redOutput, Mat blueOutput, Mat yellowOutput, Mat blackOutput, Mat canvas){
        //Find Contours
        List<MatOfPoint> hullsRed = compute.findHulls(redOutput);
        List<MatOfPoint> hullsBlue = compute.findHulls(blueOutput);
        List<MatOfPoint> hullsYellow = compute.findHulls(yellowOutput);

        //populate array of detected (color only)
        List<Detected> detected = new ArrayList<Detected>();
        List<MatOfPoint> detectedHulls = new ArrayList<MatOfPoint>();

        //we will segregate the blacks
        List<Detected> blacks = new ArrayList<Detected>();

        for (MatOfPoint p : hullsRed) {
            Detected toadd = new Detected(p, Detected.Color.RED);
            if (!toadd.isBastard) {
                detected.add(toadd);
                detectedHulls.add(p);
            }
        }
        for (MatOfPoint p : hullsBlue) {
            Detected toadd = new Detected(p, Detected.Color.BLUE);
            if (!toadd.isBastard) {
                detected.add(toadd);
                detectedHulls.add(p);
            }
        }
        /*
        for (MatOfPoint p : hullsYellow) {
            Detected toadd = new Detected(p, Detected.Color.YELLOW);
            if (!toadd.isBastard) {
                detected.add(toadd);
            }
        }*/
        Mat detectedAll = new Mat(redOutput.rows(),redOutput.cols(),redOutput.type());
        compute.drawHulls(detectedHulls, detectedAll, new Scalar(255,255,255),-1);

        //limit black to regions underneath
        Imgproc.dilate(detectedAll,detectedAll,
    			Imgproc.getStructuringElement(
    					Imgproc.MORPH_RECT,
    					new Size(1,80),
    					new Point(0,0)
    				));
        detectedAll = compute.flip(detectedAll);
        blackOutput = compute.subtract(blackOutput,detectedAll);

        //cut sides of color contours. Field walls are bad.
        for(Detected d:detected) {
        	Point one = new Point(d.bounds.x,d.bounds.y+d.bounds.height*0.1);
        	Point two = new Point(d.bounds.x,d.bounds.y+d.bounds.height*0.1+regionSideClipExtensionLength);
        	Imgproc.line(blackOutput,one, two,new Scalar(new double[] {0,0,0}),1);

        	one = new Point(d.bounds.x+d.bounds.width,d.bounds.y+d.bounds.height*0.1);
        	two = new Point(d.bounds.x+d.bounds.width,d.bounds.y+d.bounds.height*0.1+regionSideClipExtensionLength);
        	Imgproc.line(blackOutput,one, two,new Scalar(new double[] {0,0,0}),1);
        }

        ArrayList<MatOfPoint> hullsBlack = compute.findHulls(blackOutput);

        for (MatOfPoint p : hullsBlack) {
            Detected toadd = new Detected(p, Detected.Color.BLACK);
            if (!toadd.isBastard) {
                blacks.add(toadd);
            }
        }

        for (Detected d : detected) {
            d.draw(canvas);
        }for (Detected d : blacks) {
            d.draw(canvas);
        }

        //process sandwiches, populate foundation ArrayList
        List<Foundation> foundations = new ArrayList<Foundation>();

        Imgproc.putText(canvas, String.valueOf(blackcut), new Point(20,20), Core.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0,0,0), 7);
        Imgproc.putText(canvas, String.valueOf(blackcut), new Point(20,20), Core.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(255,255,0), 2);

        for (Detected d : blacks) {
            for (Detected j : detected) {
                if (Math.abs(d.x - j.x) < 120 &&
                	d.bounds.y > j.bounds.y && d.bounds.y < j.bounds.y+j.bounds.height+30 &&
                    Math.abs(d.bounds.width*1.0/j.bounds.width-1)  <  0.6)   {    
                    	foundations.add(Foundation.createFoundation(d, j));
                }
            }
        }

        return foundations;
    }


}
