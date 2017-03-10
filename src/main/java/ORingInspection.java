/*
 * Institute of Technology, Blanchardstown
 * Computer Vision (Year 4)
 * O-Ring Image Inspection Assignment
 * Main Class
 * Author: Dan Flynn
 */

package main.java;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.highgui.Highgui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.net.URISyntaxException;

public class ORingInspection {

    //Global variables
    private static JFrame window;
    private static JLabel containerOriginal;
    private static JLabel containerHist;
    private static JLabel containerThresh;
    private static JLabel containerBM;
    private static JLabel containerCCL;
    private static JLabel containerResult;

    //Initialise JFrame components
    private ORingInspection() {

        //Create and set up the window.
        window = new JFrame("OpenCV O-Ring Inspection");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setLayout(new GridLayout());

        //Panels for each step of the process
        JPanel panelOriginal = new JPanel();
        JPanel panelHist = new JPanel();
        JPanel panelThresh = new JPanel();
        JPanel panelBM = new JPanel();
        JPanel panelCCL = new JPanel();
        JPanel panelResult = new JPanel();

        //Setup JLabel image containers
        containerOriginal = new JLabel("Orignal");
        containerOriginal.setHorizontalTextPosition(JLabel.CENTER);
        containerOriginal.setVerticalTextPosition(JLabel.BOTTOM);

        containerHist     = new JLabel("Histogram");
        containerHist.setHorizontalTextPosition(JLabel.CENTER);
        containerHist.setVerticalTextPosition(JLabel.BOTTOM);

        containerThresh   = new JLabel("After Thresholding");
        containerThresh.setHorizontalTextPosition(JLabel.CENTER);
        containerThresh.setVerticalTextPosition(JLabel.BOTTOM);

        containerBM       = new JLabel("After Binary Morphology");
        containerBM.setHorizontalTextPosition(JLabel.CENTER);
        containerBM.setVerticalTextPosition(JLabel.BOTTOM);

        containerCCL      = new JLabel("After CCL");
        containerCCL.setHorizontalTextPosition(JLabel.CENTER);
        containerCCL.setVerticalTextPosition(JLabel.BOTTOM);

        containerResult   = new JLabel("Analysis Result");
        containerResult.setHorizontalTextPosition(JLabel.CENTER);
        containerResult.setVerticalTextPosition(JLabel.BOTTOM);

        //Add all JLabels to the two panels
        panelOriginal.add(containerOriginal, BorderLayout.CENTER);
        panelHist.add(containerHist, BorderLayout.CENTER);
        panelThresh.add(containerThresh, BorderLayout.CENTER);
        panelBM.add(containerBM, BorderLayout.CENTER);
        panelCCL.add(containerCCL, BorderLayout.CENTER);
        panelResult.add(containerResult, BorderLayout.CENTER);

        //Add the two panels to the frame
        window.getContentPane().add(panelOriginal);
        window.getContentPane().add(panelHist);
        window.getContentPane().add(panelThresh);
        window.getContentPane().add(panelBM);
        window.getContentPane().add(panelCCL);
        window.getContentPane().add(panelResult);

        //Display the window.
        window.pack();
        window.setVisible(true);
    }

    public static void main(String[] args) {

        //Initialise JFrame components
        new ORingInspection();

        //Load native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        //Instantiate variables
        Mat imgInput = new Mat();
        int i=0; //Image counter

        //noinspection InfiniteLoopStatement
        while(true) {

            ///READ ORIGINAL IMAGE///
            BufferedImage imgOriginal = null;
            try {
                //Get next image file
                File file = new File(ORingInspection.class.getResource("/oring-images/Oring" + (i%15+1) + ".jpg").toURI());
                //Load Greyscale image with OpenCV
                imgInput = Highgui.imread(file.getPath(),0);
                imgOriginal = Mat2BufferedImage(imgInput);
            }
            catch (URISyntaxException ignored) {}

            i++; //Advance to next


            ///PROCESS IMAGES///
            //1. Calculate and draw the image histogram
            Mat histImg = new Mat(220,220, CvType.CV_8UC3);
            int [] h = hist(imgInput);
            drawHistogram(histImg, h);
            BufferedImage imgHistogram = Mat2BufferedImage(histImg);

            //2. Threshold the image using histogram
            int t = calculateOtsu(imgInput, h);
            threshold(imgInput, t);
            BufferedImage imgThreshold = Mat2BufferedImage(imgInput);

            //3. Close any small holes in the rings
            dilate(imgInput);
            erode(imgInput);
            BufferedImage imgBM = Mat2BufferedImage(imgInput);

            //4. Perform CCL to remove any spurious artifacts
            performCCL(imgInput);
            BufferedImage imgCCL = Mat2BufferedImage(imgInput);

            //5. Analyse regions to classify the Oring (Pass/Fail)
            BufferedImage imgResult = Mat2BufferedImage(imgInput);

            //6. Measure the image processing time (text annotation)


            ///DISPLAY IMAGES///
            //Add current images to their JPanels
            containerOriginal.setIcon(new ImageIcon(imgOriginal));
            containerHist.setIcon(new ImageIcon(imgHistogram));
            containerThresh.setIcon(new ImageIcon(imgThreshold));
            containerBM.setIcon(new ImageIcon(imgBM));
            containerCCL.setIcon(new ImageIcon(imgCCL));
            containerResult.setIcon(new ImageIcon(imgResult));

            //Repack the JFrame
            window.pack();

            //Advance to next every 1 second
            try {Thread.sleep(1500);}
            catch (InterruptedException e) {e.printStackTrace();}
        }
    }

    //Calculate image histogram
    private static int [] hist(Mat imgInput) {

        int hist [] = new int[256];
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels

        for(byte value : data) {
            hist[(value & 0xff)]++;
        }
        return hist;
    }

    //Find the largest peak in the histogram
    private static int findHistPeak(int [] hist) {

        int largestValue = hist[0];
        int indexOfLargest = 0;
        for(int i=0; i<hist.length; i++) {
            if(hist[i] > largestValue) {
                largestValue = hist[i];
                indexOfLargest = i;
            }
        }
        return indexOfLargest-50;
    }

    //Draw image histogram
    private static void drawHistogram(Mat imgInput, int[] hist) {

        //Define histogram scale by finding max hist value
        int max = 0;
        for (int value : hist) {
            if (value > max)
                max = value;
        }
        int scale = max / 256;

        //Draw histogram object
        for (int i = 0; i < hist.length - 1; i++) {
            Core.line(imgInput, new Point(i + 1, imgInput.rows() - (hist[i] / scale) + 1), new Point(i + 2, imgInput.rows() - (hist[i + 1] / scale) + 1), new Scalar(0, 0, 255));
        }
    }

    //Otsu's Method Global Thresholding
    private static int calculateOtsu(Mat imgInput, int [] histData){

        //Process input image
        byte srcData[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, srcData); //Get all pixels

        //Calculate the histogram
        int ptr =0;
        while(ptr < srcData.length){
            int h = 0xff & srcData[ptr];
            histData[h] ++;
            ptr ++;
        }

        //Total number of pixels
        int total = srcData.length;
        float sum =  0;
        for (int t =0; t < 256; t++){
            sum += t * histData[t];
        }

        float sumB =0;
        int wB = 0;
        int wF;

        float varMax = 0;
        int threshold = 0;

        for(int t = 0; t < 256; t++){
            wB += histData[t];            //Weight of background
            if(wB == 0) continue;

            wF = total - wB;              //Weight of foreground
            if(wF == 0) break;

            sumB += (float) (t * histData[t]);

            float mB = sumB / wB;         //Mean of background
            float mF = (sum - sumB) / wF; //Mean of foreground

            //Calculate between class variance
            float varBetween = (float)wB * (float)wF * (mB - mF) * (mB - mF);

            //Check if new maximum found
            if(varBetween > varMax){
                varMax = varBetween;
                threshold = t;
            }
        }

        return threshold - 50;
    }

    //Process image threshold on input image (convert to binary)
    private static void threshold(Mat imgInput, int t) {

        //Note that we need to use an & with 0xff here.
        //This is because Java uses signed two's complement types.
        //The & operation will give us the pixel in the range we are used to (0..255).

        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data);
        for (int i=0;i<data.length;i++)
        {
            int unsigned = (data[i] & 0xff);
            if (unsigned > t)
                data[i] = (byte)0;
            else
                data[i] = (byte)255;
        }
        imgInput.put(0, 0, data);
    }

    //Dilate the image input
    private static void dilate(Mat imgInput) {

        //Build byte array of input image
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels
        byte copy[] = data.clone(); //Copy of data byte array

        //Loops through 48400 pixels (220x200 images)
        for(int i=0; i<data.length; i++) {

            //Get all 8 neighbour pixels to the current pixel
            int [] neighbours = {i+1, i-1, i-imgInput.cols(), i+imgInput.cols(), i+imgInput.cols()+1, i+imgInput.cols()-1, i-imgInput.cols()+1, i-imgInput.cols()-1};

            try {
                //Loops through all 8 neighbouring pixels
                for(int neighbour : neighbours) {
                    if((copy[neighbour] & 0xff) == 255) {
                        data[i] = (byte) 255;
                    }
                }
            }
            //Ignore ArrayIndexOutOfBounds exceptions
            catch(ArrayIndexOutOfBoundsException ignored) {}
        }

        //Replace imgInput with dilated image data
        imgInput.put(0, 0, data);
    }

    //Erode the image input
    private static void erode(Mat imgInput) {

        //Build byte array of input image
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels
        byte copy[] = data.clone(); //Copy of data byte array

        //Loops through 48400 pixels (220x200 images)
        for (int i=0; i<data.length; i++) {

            //Get all 8 neighbour pixels to the current pixel
            int [] neighbours = {i+1, i-1, i-imgInput.cols(), i+imgInput.cols(), i+imgInput.cols()+1, i+imgInput.cols()-1, i-imgInput.cols()+1, i-imgInput.cols()-1};

            try {
                //Loops through all 8 neighbouring pixels
                for(int neighbour : neighbours) {
                    if ((copy[neighbour] & 0xff) == 0) {
                        data[i] = (byte) 0;
                    }
                }
            }
            //Ignore ArrayIndexOutOfBounds exceptions
            catch(ArrayIndexOutOfBoundsException ignored) {}
        }

        //Replace ingInput with eroded image data
        imgInput.put(0, 0, data);
    }

    //Perform Connected Component Labelling (CCL) on imgInput
    private static byte[] performCCL(Mat imgInput) {

        //Build two byte arrays from input image
        byte imgData[] = new byte[imgInput.rows() * imgInput.cols()];
        byte label[]   = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, imgData); //Get all pixels

        //Instantiate variables
        int currentLabel = 1; //Label 1 is the ring
        int pixel;
        DataQueue queue = new DataQueue();

        //Loop through all pixels
        for (int i = 0; i < imgData.length; i++) {

            if ((imgData[i] & 0xff) == 255 && label[i] == 0) {

                label[i] = (byte) (currentLabel);

                try {
                    //Add current pixel to the queue
                    queue.enQueue(i);

                    //While queue is not empty
                    while (!queue.isEmpty()) {

                        pixel = queue.deQueue();

                        //Get all 8 neighbouring pixels
                        int [] neighbours = {pixel + 1, pixel - 1,
                                pixel - imgInput.cols(), pixel + imgInput.cols(),
                                pixel + imgInput.cols() + 1, pixel + imgInput.cols() - 1,
                                pixel - imgInput.cols() + 1, pixel - imgInput.cols() - 1};

                        //Foreach neighbour pixel
                        for (int neighbour : neighbours) {
                            if ((imgData[neighbour] & 0xff) == 255 && label[neighbour] == 0) {
                                label[neighbour] = (byte) (currentLabel);
                                queue.enQueue(neighbour);
                            }
                        }
                    }
                    currentLabel += 1; //Next component is not part of the ring
                }
                catch (ArrayIndexOutOfBoundsException ignored) {}
            }
        }

        //Remove imperfections using CCL data
        for (int i=0; i<imgData.length; i++) {
            if ((label[i] & 0xff) > 1) {
                imgData[i] = 0; //Set pixel to black
            }
        }

        //Save all processed pixels to label
        imgInput.put(0, 0, imgData);
        return imgData;
    }

    //Convert to BufferedImage for JLabel
    private static BufferedImage Mat2BufferedImage(Mat m) {

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte [] b = new byte[bufferSize];

        m.get(0,0,b); //Get all pixels
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);

        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);

        return image;
    }
}